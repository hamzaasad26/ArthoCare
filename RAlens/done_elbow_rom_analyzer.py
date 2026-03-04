"""
Elbow Joint ROM Analysis — Rheumatoid Arthritis Detection
MediaPipe Pose Tracking | AAOS Clinical Standards

Flow:
  LEFT  arm → Neutral → Extension → Flexion → report screen (12s)
  RIGHT arm → Neutral → Extension → Flexion → report screen (12s)
  Combined JSON + TXT report saved to disk

Author: Elbow ROM Analysis System  |  2025
"""

import cv2
import mediapipe as mp
import numpy as np
import time
import json
import os
from dataclasses import dataclass, asdict
from typing import List, Tuple, Optional
from enum import Enum
from collections import deque
from datetime import datetime


# ═══════════════════════════════════════════════════════════
#  CONFIGURATION
# ═══════════════════════════════════════════════════════════

WINDOW_W, WINDOW_H = 960, 600

class TestState(Enum):
    WAITING          = 0
    NEUTRAL_BASELINE = 1
    EXTENSION_TEST   = 2   # straighten fully  (FIRST)
    FLEXION_TEST     = 3   # bend fully         (SECOND)
    COMPLETE         = 4

THRESH = {
    'neutral_max':     20,
    'extension_max':   15,   # acceptable extension (≤15° = good)
    'flexion_normal': 145,
    'flexion_min':    120,
}

STAB = {
    'buf':         14,
    'variance':    12.0,
    'hold_sec':     2.0,
    'plateau_win': 18,
    'plateau_tol':  4.0,
}

REPORT_SECS = 12

# BGR colours
C_CYAN   = (255, 220,   0)
C_GREEN  = (  0, 230,   0)
C_ORANGE = (  0, 160, 255)
C_RED    = (  0,   0, 220)
C_WHITE  = (240, 240, 240)
C_YELLOW = (  0, 210, 255)
C_LBLUE  = (255, 200, 100)


# ═══════════════════════════════════════════════════════════
#  DATA CLASS
# ═══════════════════════════════════════════════════════════

@dataclass
class ElbowResult:
    side: str
    neutral_angle:     float
    max_flexion:       float
    min_extension:     float
    flexion_normal:    bool
    extension_normal:  bool
    flexion_deficit:   float
    extension_deficit: float
    flare_risk:        float
    risk_level:        str
    timestamp: str
    abnormalities: List[str]
    suggestions:  List[str]


# ═══════════════════════════════════════════════════════════
#  CAMERA SELECTION
# ═══════════════════════════════════════════════════════════

def select_camera():
    print("\n" + "─"*50)
    print("  CAMERA SELECTION")
    print("─"*50)
    print("  1.  Built-in Webcam")
    print("  2.  DroidCam  (WiFi)")
    print("─"*50)
    while True:
        c = input("  Enter 1 or 2: ").strip()
        if c == '1':
            return 0
        if c == '2':
            ip   = input("  DroidCam IP  (default 192.168.100.48): ").strip() or "192.168.100.48"
            port = input("  Port         (default 4747): ").strip() or "4747"
            return f"http://{ip}:{port}/video"
        print("  Enter 1 or 2")


# ═══════════════════════════════════════════════════════════
#  GEOMETRY
# ═══════════════════════════════════════════════════════════

def elbow_angle(shoulder, elbow, wrist) -> float:
    v1 = shoulder - elbow
    v2 = wrist    - elbow
    n1, n2 = np.linalg.norm(v1), np.linalg.norm(v2)
    if n1 < 1e-6 or n2 < 1e-6:
        return 0.0
    cos_a    = np.clip(np.dot(v1/n1, v2/n2), -1.0, 1.0)
    interior = np.degrees(np.arccos(cos_a))
    return max(0.0, 180.0 - interior)          # 0 = straight, 145 = fully bent


# ═══════════════════════════════════════════════════════════
#  LANDMARK HELPER
# ═══════════════════════════════════════════════════════════

def get_arm(pose_lm, side: str):
    P = mp.solutions.pose.PoseLandmark
    if side == 'left':
        si, ei, wi = P.LEFT_SHOULDER.value, P.LEFT_ELBOW.value, P.LEFT_WRIST.value
    else:
        si, ei, wi = P.RIGHT_SHOULDER.value, P.RIGHT_ELBOW.value, P.RIGHT_WRIST.value
    lm = pose_lm.landmark
    sh  = np.array([lm[si].x, lm[si].y, lm[si].z])
    el  = np.array([lm[ei].x, lm[ei].y, lm[ei].z])
    wr  = np.array([lm[wi].x, lm[wi].y, lm[wi].z])
    vis = min(lm[si].visibility, lm[ei].visibility, lm[wi].visibility)
    return sh, el, wr, vis, si, ei, wi


# ═══════════════════════════════════════════════════════════
#  POSTURE VALIDATION  — clear user guidance
# ═══════════════════════════════════════════════════════════

def validate(angle: float, state: TestState) -> Tuple[bool, str, str]:
    """Returns (ok, status_msg, direction_hint)"""

    if state == TestState.NEUTRAL_BASELINE:
        if angle <= THRESH['neutral_max']:
            return True,  f"Good, arm straight  ({angle:.0f} deg)", ""
        diff = angle - THRESH['neutral_max']
        return False, f"Straighten arm  ({angle:.0f} deg  need < {THRESH['neutral_max']} deg)", \
               f"  Lower your forearm by about {diff:.0f} deg"

    elif state == TestState.EXTENSION_TEST:
        if angle <= THRESH['extension_max']:
            return True,  f"Good extension  ({angle:.0f} deg)", ""
        elif angle <= 45:
            return True,  f"Keep straightening  ({angle:.0f} deg)", \
                   f"  Push forearm down a little more"
        else:
            return False, f"Straighten arm  ({angle:.0f} deg  need < {THRESH['extension_max']} deg)", \
                   f"  Lower your forearm and try to reach 0 deg"

    elif state == TestState.FLEXION_TEST:
        if angle >= 110:
            return True,  f"Good flexion  ({angle:.0f} deg)", ""
        elif angle >= 60:
            return True,  f"Keep bending  ({angle:.0f} deg)", \
                   f"  Lift forearm up and bend the elbow more"
        else:
            return False, f"Bend elbow more  ({angle:.0f} deg  need > 60 deg)", \
                   f"  Lift forearm up toward your shoulder"

    return True, "Position OK", ""


# ═══════════════════════════════════════════════════════════
#  GUI HELPERS
# ═══════════════════════════════════════════════════════════

def rrect(img, x1, y1, x2, y2, r, color, alpha=0.78):
    ov = img.copy()
    cv2.rectangle(ov, (x1+r, y1), (x2-r, y2), color, -1)
    cv2.rectangle(ov, (x1, y1+r), (x2, y2-r), color, -1)
    for cx, cy in [(x1+r,y1+r),(x2-r,y1+r),(x1+r,y2-r),(x2-r,y2-r)]:
        cv2.circle(ov, (cx,cy), r, color, -1)
    cv2.addWeighted(ov, alpha, img, 1-alpha, 0, img)


def txt(img, text, x, y, scale=0.58, color=C_WHITE, thick=1, bold=False):
    f = cv2.FONT_HERSHEY_DUPLEX
    if bold:
        cv2.putText(img, text, (x, y), f, scale, (0,0,0), thick+2)
    cv2.putText(img, text, (x, y), f, scale, color, thick)


def arc_gauge(img, cx, cy, rad, angle, max_a=145):
    cv2.ellipse(img, (cx,cy), (rad,rad), -90, 0, max_a,
                (55,55,55), 8, cv2.LINE_AA)
    if angle > 2:
        pct = min(angle/max_a, 1.0)
        col = C_GREEN if pct > 0.55 else C_ORANGE
        cv2.ellipse(img, (cx,cy), (rad,rad), -90, 0, int(angle),
                    col, 8, cv2.LINE_AA)
    # moving dot
    rad_a = np.radians(-90 + min(angle, max_a))
    tx = int(cx + rad * np.cos(rad_a))
    ty = int(cy + rad * np.sin(rad_a))
    cv2.circle(img, (tx,ty), 10, C_YELLOW, -1, cv2.LINE_AA)
    cv2.circle(img, (tx,ty),  5, C_WHITE,  -1, cv2.LINE_AA)


def draw_skeleton(frame, pose_lm, side, w, h, angle):
    P  = mp.solutions.pose.PoseLandmark
    if side == 'left':
        si,ei,wi = P.LEFT_SHOULDER.value, P.LEFT_ELBOW.value, P.LEFT_WRIST.value
    else:
        si,ei,wi = P.RIGHT_SHOULDER.value, P.RIGHT_ELBOW.value, P.RIGHT_WRIST.value
    lm = pose_lm.landmark
    sp = (int(lm[si].x*w), int(lm[si].y*h))
    ep = (int(lm[ei].x*w), int(lm[ei].y*h))
    wp = (int(lm[wi].x*w), int(lm[wi].y*h))

    # glow
    cv2.line(frame, sp, ep, (0,80,0),   10, cv2.LINE_AA)
    cv2.line(frame, ep, wp, (0,60,100),  9, cv2.LINE_AA)
    cv2.line(frame, sp, ep, C_GREEN,     4, cv2.LINE_AA)
    cv2.line(frame, ep, wp, C_LBLUE,     3, cv2.LINE_AA)
    for pt,col,r in [(sp,C_YELLOW,9),(ep,C_CYAN,12),(wp,C_LBLUE,9)]:
        cv2.circle(frame, pt, r+3, (0,0,0), -1, cv2.LINE_AA)
        cv2.circle(frame, pt, r,   col,     -1, cv2.LINE_AA)
    txt(frame, f"{angle:.0f} deg", ep[0]+14, ep[1]-14, 0.75, C_YELLOW, 2, bold=True)


# ═══════════════════════════════════════════════════════════
#  LIVE UI
# ═══════════════════════════════════════════════════════════

def draw_live(frame, pose_lm, angle, state, side,
              ok, msg, hint, max_flex, min_ext,
              countdown, stable, w, h):

    draw_skeleton(frame, pose_lm, side, w, h, angle)

    # TOP BAR
    rrect(frame, 0, 0, w, 68, 0, (14,14,24), alpha=0.88)
    step_map = {
        TestState.NEUTRAL_BASELINE: ("STEP 1/3", "Hold arm straight down at your side"),
        TestState.EXTENSION_TEST:   ("STEP 2/3", "Straighten elbow - push forearm down to 0 deg"),
        TestState.FLEXION_TEST:     ("STEP 3/3", "Bend elbow fully - lift forearm up to shoulder"),
    }
    sl, si_ = step_map.get(state, ("",""))
    txt(frame, sl, 14, 24,  0.52, C_CYAN,  1)
    txt(frame, si_, 14, 54, 0.62, C_WHITE, 1)

    # SIDE BADGE
    bc = (160,50,0) if side=='left' else (0,70,160)
    rrect(frame, w-128, 6, w-6, 62, 6, bc, alpha=0.92)
    txt(frame, f"{side.upper()} ARM", w-120, 42, 0.64, C_WHITE, 2)

    # ARC GAUGE
    gcx, gcy = w-95, h-120
    arc_gauge(frame, gcx, gcy, 72, angle)
    txt(frame, f"{angle:.0f} deg", gcx-36, gcy+20, 0.82, C_YELLOW, 2, bold=True)
    txt(frame, "ANGLE",         gcx-28, gcy+42, 0.44, C_WHITE,  1)

    # POSTURE FEEDBACK
    fy = h-105
    rrect(frame, 8, fy, 530, h-8, 8,
          (0,55,0) if ok else (55,0,0), alpha=0.78)
    fc = C_GREEN if ok else C_RED
    txt(frame, msg,  18, fy+30, 0.60, fc, 2)
    if hint:
        txt(frame, hint, 18, fy+58, 0.53, C_ORANGE, 1)

    # PROGRESS BARS
    rrect(frame, 8, 82, 215, 228, 8, (18,18,32), alpha=0.78)
    txt(frame, "PROGRESS", 18, 106, 0.48, C_CYAN, 1)

    bw = 188
    # Extension bar
    txt(frame, f"Extension: {min_ext if min_ext<180 else 0:.0f} deg", 18, 134, 0.46, C_WHITE, 1)
    ext_v = min_ext if min_ext < 180 else 45.0
    ep_ = max(0.0, 1.0 - ext_v/45.0)
    cv2.rectangle(frame, (18,142), (18+bw,154), (50,50,50), -1)
    cv2.rectangle(frame, (18,142), (18+int(bw*ep_),154),
                  C_GREEN if ext_v<=THRESH['extension_max'] else C_ORANGE, -1)

    # Flexion bar
    txt(frame, f"Flexion:   {max_flex:.0f} deg", 18, 176, 0.46, C_WHITE, 1)
    fp_ = min(max_flex/THRESH['flexion_normal'], 1.0)
    cv2.rectangle(frame, (18,184), (18+bw,196), (50,50,50), -1)
    cv2.rectangle(frame, (18,184), (18+int(bw*fp_),196),
                  C_GREEN if max_flex>=THRESH['flexion_min'] else C_ORANGE, -1)

    # COUNTDOWN
    if countdown is not None and countdown > 0:
        rrect(frame, w//2-100, h//2-44, w//2+100, h//2+44, 14,
              (0,90,0), alpha=0.90)
        txt(frame, f"Hold  {countdown:.1f}s", w//2-75, h//2+12,
            0.88, C_GREEN, 2, bold=True)
    elif stable and ok:
        txt(frame, "STABLE", w-165, 106, 0.54, C_GREEN, 2)
    else:
        txt(frame, "Stabilising...", w-210, 106, 0.48, C_ORANGE, 1)

    return frame


# ═══════════════════════════════════════════════════════════
#  REPORT SCREEN
# ═══════════════════════════════════════════════════════════

def draw_report(result: ElbowResult, secs_left: float):
    img = np.zeros((WINDOW_H, WINDOW_W, 3), dtype=np.uint8)
    img[:] = (16, 16, 26)

    # header
    rrect(img, 0, 0, WINDOW_W, 56, 0, (28,28,46), alpha=1.0)
    txt(img, "ELBOW ROM REPORT", WINDOW_W//2-148, 38,
        0.92, C_CYAN, 2, bold=True)
    txt(img, f"Closing in {secs_left:.0f}s",
        WINDOW_W-185, 38, 0.48, (130,130,130), 1)

    cv2.line(img, (28,60), (WINDOW_W-28,60), (55,55,75), 1)

    # side badge
    sc = (160,50,0) if result.side=='left' else (0,70,160)
    rrect(img, 28, 68, 196, 106, 6, sc, alpha=0.92)
    txt(img, f"{result.side.upper()} ELBOW", 40, 96, 0.64, C_WHITE, 2)
    txt(img, result.timestamp, WINDOW_W-340, 92, 0.40, (130,130,130), 1)

    y = 118
    cv2.line(img, (28,y), (WINDOW_W-28,y), (38,38,52), 1)

    # MEASUREMENTS
    y += 18
    txt(img, "MEASUREMENTS", 34, y+14, 0.56, C_CYAN, 1)
    y += 34

    def mrow(label, val, ref, ok, yy):
        tag = "NORMAL" if ok else "REDUCED"
        tc  = C_GREEN  if ok else C_RED
        bg  = (24,38,24) if ok else (42,18,18)
        rrect(img, 32, yy-20, WINDOW_W-32, yy+10, 4, bg, alpha=0.88)
        txt(img, label,              38,            yy,   0.50, C_WHITE,  1)
        txt(img, f"{val:.0f} deg",   310,           yy,   0.58, C_YELLOW, 2)
        txt(img, ref,                370,           yy,   0.43, (155,155,155), 1)
        txt(img, tag, WINDOW_W-128,  yy,            0.52, tc,   2)

    mrow("Max Flexion    (bend elbow up)",
         result.max_flexion,
         f"normal >= {THRESH['flexion_normal']} deg",
         result.flexion_normal, y);  y += 38

    mrow("Min Extension  (straighten arm down)",
         result.min_extension,
         f"normal <= {THRESH['extension_max']} deg",
         result.extension_normal, y); y += 44

    # flare-up risk row
    rrect(img, 32, y-18, WINDOW_W-32, y+12, 4, (26,26,46), alpha=0.88)
    txt(img, "Estimated flare-up risk", 38, y, 0.50, C_WHITE, 1)
    txt(img, f"{result.flare_risk:.0f}%", 310, y, 0.58, C_YELLOW, 2)
    bar_w = 260
    bx1   = 370
    cv2.rectangle(img, (bx1, y-8), (bx1+bar_w, y+4), (50,50,50), -1)
    rw = int(bar_w * (max(0.0, min(result.flare_risk, 100.0)) / 100.0))
    rc = C_GREEN if result.flare_risk < 25 else C_ORANGE
    cv2.rectangle(img, (bx1, y-8), (bx1+rw, y+4), rc, -1)
    y += 40

    cv2.line(img, (28,y), (WINDOW_W-28,y), (38,38,52), 1);  y += 16

    # CLINICAL SUMMARY
    txt(img, "CLINICAL SUMMARY", 34, y+14, 0.56, C_CYAN, 1);  y += 34
    overall_ok = result.flexion_normal and result.extension_normal

    if overall_ok:
        bg2 = (18,44,18)
        lines = [
            "  Elbow ROM within AAOS normal limits.",
            f"  Flexion {result.max_flexion:.0f} deg  (>= {THRESH['flexion_normal']} deg)   |   "
            f"Extension {result.min_extension:.0f} deg  (<= {THRESH['extension_max']} deg)"
        ]
        lc = C_GREEN
    else:
        bg2 = (44,18,18)
        lines = []
        if not result.flexion_normal:
            lines.append(
                f"  Flexion restricted - {result.max_flexion:.0f} deg  "
                f"(deficit {result.flexion_deficit:.0f} deg below {THRESH['flexion_normal']} deg)"
            )
        if not result.extension_normal:
            lines.append(
                f"  Extension restricted - {result.min_extension:.0f} deg  "
                f"(deficit {result.extension_deficit:.0f} deg above {THRESH['extension_max']} deg)"
            )
        lc = C_ORANGE

    rrect(img, 32, y-4, WINDOW_W-32, y+len(lines)*30+10, 6, bg2, alpha=0.88)
    for ln in lines:
        txt(img, ln, 42, y+20, 0.50, lc, 1);  y += 30
    y += 20

    cv2.line(img, (28,y), (WINDOW_W-28,y), (38,38,52), 1);  y += 16

    # RECOMMENDATIONS
    txt(img, "RECOMMENDATIONS", 34, y+14, 0.56, C_CYAN, 1);  y += 34
    if not result.abnormalities:
        recs = ["Continue current exercise and treatment plan.",
                "Routine elbow ROM check every 3 months."]
        rc = C_GREEN
    else:
        recs = ["Clinical evaluation recommended — ROM restriction detected.",
                "Consider rheumatology / physiotherapy consultation.",
                "Elbow stretching: 3x daily, 10 reps each direction.",
                "Heat therapy before exercises may improve flexibility."]
        rc = (200, 175, 100)
    for r in recs:
        txt(img, f"  {r}", 42, y+18, 0.47, rc, 1);  y += 28

    # progress bar
    bw2 = WINDOW_W - 40
    bf2 = int(bw2 * (secs_left / REPORT_SECS))
    cv2.rectangle(img, (20, WINDOW_H-10), (20+bw2, WINDOW_H-3), (45,45,45), -1)
    cv2.rectangle(img, (20, WINDOW_H-10), (20+bf2,  WINDOW_H-3), C_CYAN,    -1)

    return img


# ═══════════════════════════════════════════════════════════
#  CLINICAL ANALYSIS
# ═══════════════════════════════════════════════════════════

def analyze(neutral, max_flex, min_ext, side) -> ElbowResult:
    # ensure we are working with plain Python floats (not numpy scalars)
    neutral   = float(neutral)
    max_flex  = float(max_flex)
    min_ext   = float(min_ext)
    flex_min  = float(THRESH['flexion_min'])
    flex_norm = float(THRESH['flexion_normal'])
    ext_max   = float(THRESH['extension_max'])
    neut_max  = float(THRESH['neutral_max'])

    flex_ok = max_flex >= flex_min
    ext_ok  = min_ext  <= ext_max
    fd = max(0.0, flex_norm - max_flex)
    ed = max(0.0, min_ext - ext_max)

    # heuristic flare-up risk score (0–100%), based on ROM deficits
    neutral_excess = max(0.0, neutral - neut_max)
    nsev = min(neutral_excess / 20.0, 1.0)
    fsev = min(fd / 40.0, 1.0) if not flex_ok else 0.0
    esev = min(ed / 20.0, 1.0) if not ext_ok else 0.0
    sev_score = 0.2 * nsev + 0.5 * fsev + 0.3 * esev
    flare_risk = float(min(95.0, 5.0 + sev_score * 90.0))

    abn = []
    if not flex_ok:
        abn.append(
            f"Reduced flexion: {max_flex:.0f} deg (normal >= {THRESH['flexion_normal']} deg, "
            f"deficit {fd:.0f} deg)"
        )
    if not ext_ok:
        abn.append(
            f"Restricted extension: {min_ext:.0f} deg (normal <= {THRESH['extension_max']} deg, "
            f"deficit {ed:.0f} deg)"
        )

    # qualitative risk level and elbow-specific suggestions
    if flare_risk < 25:
        risk_level = "low"
        suggestions = [
            "Maintain current exercise and treatment plan.",
            "Routine elbow ROM check every 3–6 months.",
        ]
    elif flare_risk < 60:
        risk_level = "moderate"
        suggestions = [
            "Increase supervised elbow ROM and strengthening exercises.",
            "Monitor for pain, swelling or morning stiffness.",
            "Discuss disease control and physiotherapy program at next clinic visit.",
        ]
    else:
        risk_level = "high"
        suggestions = [
            "Clinical review recommended - restricted ROM with higher flare-up risk.",
            "Consider rheumatology / physiotherapy consultation soon.",
            "Short-term increase in ROM and stretching exercises as tolerated.",
            "Monitor closely for flare symptoms (pain, swelling, warmth).",
        ]

    return ElbowResult(
        side=side,
        neutral_angle=neutral,
        max_flexion=max_flex,
        min_extension=min_ext,
        flexion_normal=bool(flex_ok),
        extension_normal=bool(ext_ok),
        flexion_deficit=fd,
        extension_deficit=ed,
        flare_risk=flare_risk,
        risk_level=risk_level,
        timestamp=datetime.now().strftime("%Y-%m-%d %H:%M:%S"),
        abnormalities=abn,
        suggestions=suggestions,
    )


# ═══════════════════════════════════════════════════════════
#  REPORT SAVING
# ═══════════════════════════════════════════════════════════

def save_report(folder, pid, left_r, right_r):
    os.makedirs(folder, exist_ok=True)
    ts = datetime.now().strftime("%Y-%m-%d %H:%M:%S")

    jp = os.path.join(folder, f"{pid}_elbow_report.json")
    with open(jp, 'w') as f:
        json.dump(
            {
                'patient_id': pid,
                'test_date': ts,
                'left_elbow':  asdict(left_r)  if left_r  else None,
                'right_elbow': asdict(right_r) if right_r else None,
                'reference': {
                    'flexion_normal_deg': THRESH['flexion_normal'],
                    'flexion_functional_arc_deg': [30, 130],
                    'extension_normal_deg': 0,
                    'extension_upper_limit_deg': THRESH['extension_max'],
                    'notes': (
                        "ROM thresholds based on common orthopaedic references; "
                        "flare-up risk is a heuristic score from ROM deficits and is "
                        "NOT a formal medical diagnosis."
                    ),
                },
            },
            f,
            indent=2,
        )

    tp = os.path.join(folder, f"{pid}_elbow_summary.txt")
    with open(tp, 'w', encoding='utf-8') as f:
        sep = "=" * 60
        f.write(sep + "\n  ELBOW ROM ANALYSIS REPORT\n" + sep + "\n")
        f.write(f"  Patient ID : {pid}\n  Test Date  : {ts}\n" + sep + "\n\n")
        for res, lbl in [(left_r, "LEFT ELBOW"), (right_r, "RIGHT ELBOW")]:
            if not res: continue
            f.write(f"  {lbl}\n  " + "─"*42 + "\n")
            f.write(f"  Neutral      : {res.neutral_angle:.1f} deg  (normal <= {THRESH['neutral_max']} deg)\n")
            f.write(
                f"  Max Flexion  : {res.max_flexion:.1f} deg  (normal >= {THRESH['flexion_normal']} deg)"
                f"  -> {'NORMAL' if res.flexion_normal else f'REDUCED deficit {res.flexion_deficit:.0f} deg'}\n"
            )
            f.write(
                f"  Min Extension: {res.min_extension:.1f} deg  (normal <= {THRESH['extension_max']} deg)"
                f"  -> {'NORMAL' if res.extension_normal else f'RESTRICTED deficit {res.extension_deficit:.0f} deg'}\n"
            )
            f.write(
                f"  Flare-up risk: {res.flare_risk:.0f}% ({res.risk_level.upper()} risk, heuristic)\n"
            )
            f.write(
                f"  Status       : "
                f"{'NORMAL' if res.flexion_normal and res.extension_normal else 'RESTRICTION DETECTED'}\n"
            )
            if res.abnormalities:
                f.write("  Abnormalities:\n")
                for a in res.abnormalities: f.write(f"    - {a}\n")
            if res.suggestions:
                f.write("  Suggestions:\n")
                for s in res.suggestions: f.write(f"    - {s}\n")
            f.write("\n")
        issues = []
        if left_r  and left_r.abnormalities:  issues += left_r.abnormalities
        if right_r and right_r.abnormalities: issues += right_r.abnormalities
        f.write("  RECOMMENDATIONS\n  " + "─"*42 + "\n")
        if not issues:
            f.write("  - Elbow ROM normal. Continue treatment plan.\n")
            f.write("  - Routine monitoring every 3 months.\n")
        else:
            f.write("  - Clinical evaluation recommended.\n")
            f.write("  - Consider rheumatology / physiotherapy referral.\n")
            f.write("  - Elbow stretching 3x daily, 10 reps each direction.\n")
            f.write("  - Heat therapy before exercises may help.\n")
        f.write("\n  AAOS REFERENCE\n  " + "─"*42 + "\n")
        f.write(f"  Flexion   : 0 to {THRESH['flexion_normal']} deg\n")
        f.write(f"  Extension : <= {THRESH['extension_max']} deg\n" + sep + "\n")

    print(f"  TXT report  -> {tp}")
    print(f"  JSON report -> {jp}")


# ═══════════════════════════════════════════════════════════
#  SINGLE-ARM ANALYZER
# ═══════════════════════════════════════════════════════════

class ArmAnalyzer:
    def __init__(self, cap, pose, side, pid, out):
        self.cap   = cap
        self.pose  = pose
        self.side  = side
        self.pid   = pid
        self.out   = out
        # start directly in measurement without requiring SPACE
        self.state = TestState.NEUTRAL_BASELINE
        self.max_flex = 0.0
        self.min_ext  = 180.0
        self.neutral  = 0.0
        self.buf      = deque(maxlen=STAB['buf'])
        self.hist     = []
        self.hold_t   = None

    def _stable(self, a):
        self.buf.append(a)
        if len(self.buf) < STAB['buf']: return False
        return float(np.var(list(self.buf))) < STAB['variance']

    def _plateaued(self, a):
        if self.state not in (TestState.FLEXION_TEST, TestState.EXTENSION_TEST):
            return True
        self.hist.append(a)
        if len(self.hist) > STAB['plateau_win']: self.hist.pop(0)
        if len(self.hist) < STAB['plateau_win']: return False
        return abs(float(np.mean(self.hist[-5:])) - float(np.mean(self.hist[:5]))) < STAB['plateau_tol']

    def _capture(self, frame, angle):
        cv2.imwrite(os.path.join(self.out, f"{self.pid}_{self.side}_{self.state.name}.jpg"), frame)
        if   self.state == TestState.NEUTRAL_BASELINE: self.neutral = angle;  self.state = TestState.EXTENSION_TEST
        elif self.state == TestState.EXTENSION_TEST:                           self.state = TestState.FLEXION_TEST
        elif self.state == TestState.FLEXION_TEST:                             self.state = TestState.COMPLETE
        self.hold_t = None;  self.buf.clear();  self.hist.clear()

    def run(self) -> Optional[ElbowResult]:
        while True:
            ret, raw = self.cap.read()
            if not ret: break

            raw   = cv2.flip(raw, 1)
            frame = cv2.resize(raw, (WINDOW_W, WINDOW_H))
            rgb   = cv2.cvtColor(frame, cv2.COLOR_BGR2RGB)
            res   = self.pose.process(rgb)

            # if state somehow in WAITING, immediately move to neutral baseline
            if self.state == TestState.WAITING:
                self.state = TestState.NEUTRAL_BASELINE
                continue

            # COMPLETE — report screen
            if self.state == TestState.COMPLETE:
                result = analyze(self.neutral, self.max_flex, self.min_ext, self.side)
                deadline = time.time() + REPORT_SECS
                while time.time() < deadline:
                    sl = deadline - time.time()
                    cv2.imshow("Elbow ROM Analyzer", draw_report(result, sl))
                    if (cv2.waitKey(40) & 0xFF) != 255: break
                return result

            # ACTIVE
            if res.pose_landmarks:
                sh, el, wr, vis, *_ = get_arm(res.pose_landmarks, self.side)
                if vis > 0.35:
                    a = elbow_angle(sh, el, wr)
                    if self.state == TestState.FLEXION_TEST:   self.max_flex = max(self.max_flex, a)
                    if self.state == TestState.EXTENSION_TEST: self.min_ext  = min(self.min_ext,  a)

                    ok, msg, hint = validate(a, self.state)
                    stable    = self._stable(a)
                    plateaued = self._plateaued(a)
                    ready     = ok and stable and plateaued

                    cd = None
                    if ready:
                        if self.hold_t is None: self.hold_t = time.time()
                        rem = STAB['hold_sec'] - (time.time() - self.hold_t)
                        cd  = max(0.0, rem)
                        if rem <= 0:
                            # capture annotated frame with skeleton and angle
                            cap_frame = frame.copy()
                            draw_skeleton(cap_frame, res.pose_landmarks, self.side, WINDOW_W, WINDOW_H, a)
                            self._capture(cap_frame, a)
                            continue
                    else:
                        self.hold_t = None

                    frame = draw_live(frame, res.pose_landmarks, a, self.state, self.side,
                                      ok, msg, hint, self.max_flex, self.min_ext,
                                      cd, stable, WINDOW_W, WINDOW_H)
                else:
                    rrect(frame, 8, WINDOW_H-68, 510, WINDOW_H-8, 6, (55,0,0), 0.8)
                    txt(frame, "Move closer / improve lighting", 18, WINDOW_H-30, 0.58, C_RED, 2)
            else:
                rrect(frame, 8, WINDOW_H-68, 560, WINDOW_H-8, 6, (55,0,0), 0.8)
                txt(frame, "Body not detected — step back a little", 18, WINDOW_H-30, 0.58, C_RED, 2)

            cv2.imshow("Elbow ROM Analyzer", frame)
            k = cv2.waitKey(10) & 0xFF
            if k == ord('q'): return None
            elif k == ord('r'):
                # restart this arm test automatically from neutral baseline
                self.state = TestState.NEUTRAL_BASELINE
                self.max_flex = 0.0
                self.min_ext = 180.0
                self.neutral = 0.0
                self.buf.clear()
                self.hist.clear()
                self.hold_t = None

        return None


# ═══════════════════════════════════════════════════════════
#  MAIN
# ═══════════════════════════════════════════════════════════

def main():
    print("\n" + "═"*52)
    print("  ELBOW ROM ANALYZER")
    print("  Rheumatoid Arthritis Detection")
    print("  AAOS Clinical Standards")
    print("═"*52)

    pid = input("\n  Patient ID (e.g. P001): ").strip()
    if not pid:
        print("  Patient ID required."); return

    cam = select_camera()
    out = os.path.join("elbow_output", pid)
    os.makedirs(out, exist_ok=True)

    print("\n  Connecting to camera...")
    cap = cv2.VideoCapture(cam)
    if not cap.isOpened():
        print(f"  Cannot open camera: {cam}"); return
    ret, _ = cap.read()
    if not ret:
        print("  Camera open but no frames."); cap.release(); return
    print("  Camera ready\n")

    pose = mp.solutions.pose.Pose(
        static_image_mode=False, model_complexity=1,
        min_detection_confidence=0.65, min_tracking_confidence=0.65)

    cv2.namedWindow("Elbow ROM Analyzer", cv2.WINDOW_NORMAL)
    cv2.resizeWindow("Elbow ROM Analyzer", WINDOW_W, WINDOW_H)

    print("  Starting LEFT elbow test...")
    left_r = ArmAnalyzer(cap, pose, 'left',  pid+"_L", out).run()

    right_r = None
    if left_r is not None:
        print("  Left done — starting RIGHT elbow test...")
        time.sleep(1.2)
        right_r = ArmAnalyzer(cap, pose, 'right', pid+"_R", out).run()

    if left_r or right_r:
        save_report(out, pid, left_r, right_r)
        print(f"\n  All files saved: elbow_output/{pid}/")

    cap.release()
    cv2.destroyAllWindows()
    print("  Test complete.\n")


if __name__ == "__main__":
    main()