"""
Wrist Joint ROM Analysis — Rheumatoid Arthritis Detection
MediaPipe Pose + Hands Tracking | AAOS Clinical Standards

Flow (per wrist):
  Neutral Baseline → Flexion → Extension → Radial Deviation → Ulnar Deviation
  → report screen (12 s)
  LEFT wrist first, then RIGHT wrist automatically.
  Combined JSON + TXT report saved to disk.

AAOS Normal ROM References:
  Flexion         : 0 – 80°
  Extension       : 0 – 70°
  Radial Deviation: 0 – 20°
  Ulnar Deviation : 0 – 30°

Author: Wrist ROM Analysis System | 2025
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
    NEUTRAL_BASELINE = 0   # hold wrist straight
    FLEXION_TEST     = 1   # bend hand DOWN
    EXTENSION_TEST   = 2   # push hand UP
    RADIAL_TEST      = 3   # tilt toward THUMB
    ULNAR_TEST       = 4   # tilt toward PINKY
    COMPLETE         = 5

# AAOS thresholds
THRESH = {
    'neutral_max':      10,   # wrist at rest ≤ 10° total deviation
    'flexion_normal':   80,   # AAOS normal ≥ 80°
    'flexion_min':      60,   # acceptable minimum
    'extension_normal': 70,   # AAOS normal ≥ 70°
    'extension_min':    50,   # acceptable minimum
    'radial_normal':    20,   # AAOS normal ≥ 20°
    'radial_min':       15,   # acceptable minimum
    'ulnar_normal':     30,   # AAOS normal ≥ 30°
    'ulnar_min':        20,   # acceptable minimum
}

STAB = {
    'buf':         14,
    'variance':    12.0,
    'hold_sec':     2.0,
    'plateau_win': 18,
    'plateau_tol':  3.0,
}

REPORT_SECS = 12

# BGR colour palette (matches elbow/shoulder analyzers)
C_CYAN   = (255, 220,   0)
C_GREEN  = (  0, 230,   0)
C_ORANGE = (  0, 160, 255)
C_RED    = (  0,   0, 220)
C_WHITE  = (240, 240, 240)
C_YELLOW = (  0, 210, 255)
C_LBLUE  = (255, 200, 100)
C_PURPLE = (200,  80, 200)
C_PINK   = (180,  80, 255)


# ═══════════════════════════════════════════════════════════
#  DATA CLASS
# ═══════════════════════════════════════════════════════════

@dataclass
class WristResult:
    side:               str
    neutral_angle:      float
    max_flexion:        float
    max_extension:      float
    max_radial:         float
    max_ulnar:          float
    flexion_normal:     bool
    extension_normal:   bool
    radial_normal:      bool
    ulnar_normal:       bool
    flexion_deficit:    float
    extension_deficit:  float
    radial_deficit:     float
    ulnar_deficit:      float
    flare_risk:         float
    risk_level:         str
    timestamp:          str
    abnormalities:      List[str]
    suggestions:        List[str]


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
#  GEOMETRY  — signed wrist angles using hand + pose landmarks
# ═══════════════════════════════════════════════════════════

def _signed_angle_2d(v_from: np.ndarray, v_to: np.ndarray) -> float:
    """
    Signed angle between two 2-D vectors (degrees).
    Positive = counter-clockwise, negative = clockwise.
    """
    n1 = np.linalg.norm(v_from) + 1e-8
    n2 = np.linalg.norm(v_to)   + 1e-8
    fv = v_from / n1;  tv = v_to / n2
    dot   = float(np.clip(np.dot(fv, tv), -1.0, 1.0))
    angle = np.degrees(np.arccos(dot))
    cross = fv[0]*tv[1] - fv[1]*tv[0]   # z-component of 2-D cross product
    return float(angle * (np.sign(cross) if cross != 0 else 1.0))


def get_wrist_vectors(pose_lm, hand_lm, side: str):
    """
    Returns:
      forearm_2d  : elbow → wrist  (sagittal-plane 2-D)
      flex_hand   : wrist → index-MCP  (for flexion/extension)
      dev_hand    : wrist → mid-MCP    (for radial/ulnar deviation)
      vis         : min landmark visibility
    """
    P = mp.solutions.pose.PoseLandmark
    if side == 'left':
        ei = P.LEFT_ELBOW.value;  wi = P.LEFT_WRIST.value
    else:
        ei = P.RIGHT_ELBOW.value; wi = P.RIGHT_WRIST.value

    elbow = np.array([pose_lm[ei].x, pose_lm[ei].y])
    wrist = np.array([pose_lm[wi].x, pose_lm[wi].y])
    vis   = min(pose_lm[ei].visibility, pose_lm[wi].visibility)

    # Hand landmarks (already 2-D normalised)
    index_mcp = np.array([hand_lm[5].x,  hand_lm[5].y])   # index finger MCP
    mid_mcp   = np.array([hand_lm[9].x,  hand_lm[9].y])   # middle finger MCP
    pinky_mcp = np.array([hand_lm[17].x, hand_lm[17].y])  # pinky MCP

    forearm_2d = wrist - elbow
    flex_hand  = index_mcp - wrist
    dev_hand   = mid_mcp   - wrist   # wrist → mid for deviation
    # deviation reference: across knuckles (index→pinky)
    knuckle    = pinky_mcp - index_mcp

    return forearm_2d, flex_hand, dev_hand, knuckle, wrist, elbow, vis


def compute_wrist_angle(forearm_2d, flex_hand, dev_hand, knuckle,
                        state: TestState) -> float:
    """
    Returns the relevant signed angle (degrees) for the current state.
    Flexion  : negative  (hand bends down)
    Extension: positive  (hand bends up)
    Radial   : absolute angle to thumb side
    Ulnar    : absolute angle to pinky side
    """
    if state in (TestState.NEUTRAL_BASELINE,
                 TestState.FLEXION_TEST,
                 TestState.EXTENSION_TEST):
        return _signed_angle_2d(forearm_2d, flex_hand)
    else:
        # deviation: angle between forearm axis and knuckle line
        return _signed_angle_2d(forearm_2d, knuckle)


# ═══════════════════════════════════════════════════════════
#  POSTURE VALIDATION
# ═══════════════════════════════════════════════════════════

def validate(raw_angle: float, state: TestState) -> Tuple[bool, str, str]:
    """Returns (ok, status_msg, direction_hint)."""

    if state == TestState.NEUTRAL_BASELINE:
        total = abs(raw_angle)
        if total <= THRESH['neutral_max']:
            return True, f"Good – wrist neutral  ({total:.0f} deg)", ""
        return False, f"Hold wrist in neutral position  ({total:.0f} deg  need < {THRESH['neutral_max']} deg)", ""

    elif state == TestState.FLEXION_TEST:
        flex = max(0.0, -raw_angle)          # downward bend → positive
        if flex >= 40:
            return True,  f"Good flexion  ({flex:.0f} deg)", ""
        elif flex >= 15:
            return True,  f"Increase flexion if possible  ({flex:.0f} deg)", ""
        else:
            return False, f"Perform wrist FLEXION  ({flex:.0f} deg  need > 15 deg)", ""

    elif state == TestState.EXTENSION_TEST:
        ext = max(0.0, raw_angle)            # upward bend → positive
        if ext >= 35:
            return True,  f"Good extension  ({ext:.0f} deg)", ""
        elif ext >= 15:
            return True,  f"Increase extension if possible  ({ext:.0f} deg)", ""
        else:
            return False, f"Perform wrist EXTENSION  ({ext:.0f} deg  need > 15 deg)", ""

    elif state == TestState.RADIAL_TEST:
        rad = abs(raw_angle)
        if rad >= 10:
            return True,  f"Good radial deviation  ({rad:.0f} deg)", ""
        else:
            return False, f"Perform RADIAL deviation  ({rad:.0f} deg  need > 10 deg)", ""

    elif state == TestState.ULNAR_TEST:
        uln = abs(raw_angle)
        if uln >= 10:
            return True,  f"Good ulnar deviation  ({uln:.0f} deg)", ""
        else:
            return False, f"Perform ULNAR deviation  ({uln:.0f} deg  need > 10 deg)", ""

    return True, "Position OK", ""


# ═══════════════════════════════════════════════════════════
#  GUI HELPERS  (identical style to elbow / shoulder)
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


def arc_gauge(img, cx, cy, rad, angle, max_a=80):
    cv2.ellipse(img, (cx,cy), (rad,rad), -90, 0, max_a,
                (55,55,55), 8, cv2.LINE_AA)
    clamped = min(abs(angle), max_a)
    if clamped > 1:
        pct = clamped / max_a
        col = C_GREEN if pct > 0.55 else C_ORANGE
        cv2.ellipse(img, (cx,cy), (rad,rad), -90, 0, int(clamped),
                    col, 8, cv2.LINE_AA)
    rad_a = np.radians(-90 + min(clamped, max_a))
    tx = int(cx + rad * np.cos(rad_a))
    ty = int(cy + rad * np.sin(rad_a))
    cv2.circle(img, (tx,ty), 10, C_YELLOW, -1, cv2.LINE_AA)
    cv2.circle(img, (tx,ty),  5, C_WHITE,  -1, cv2.LINE_AA)


def draw_hand_skeleton(frame, pose_lm, hand_lm, side, w, h, display_angle):
    """Draw forearm + hand skeleton overlay."""
    P = mp.solutions.pose.PoseLandmark
    if side == 'left':
        ei = P.LEFT_ELBOW.value;  wi_p = P.LEFT_WRIST.value
    else:
        ei = P.RIGHT_ELBOW.value; wi_p = P.RIGHT_WRIST.value

    def pp(lm, idx):
        return (int(lm[idx].x*w), int(lm[idx].y*h))

    ep  = pp(pose_lm, ei)
    wrp = pp(pose_lm, wi_p)

    # Forearm
    cv2.line(frame, ep, wrp, (0, 80, 0),  10, cv2.LINE_AA)
    cv2.line(frame, ep, wrp, C_GREEN,      4, cv2.LINE_AA)

    # Hand skeleton from MediaPipe Hands landmarks
    connections = [
        (0,1),(1,2),(2,3),(3,4),          # thumb
        (0,5),(5,6),(6,7),(7,8),           # index
        (0,9),(9,10),(10,11),(11,12),      # middle
        (0,13),(13,14),(14,15),(15,16),    # ring
        (0,17),(17,18),(18,19),(19,20),    # pinky
        (5,9),(9,13),(13,17),              # palm
    ]
    hl = hand_lm.landmark
    for a, b in connections:
        pa = (int(hl[a].x*w), int(hl[a].y*h))
        pb = (int(hl[b].x*w), int(hl[b].y*h))
        cv2.line(frame, pa, pb, C_LBLUE, 2, cv2.LINE_AA)

    # Key joint circles
    for pt, col, r in [(ep, C_YELLOW, 10), (wrp, C_CYAN, 12)]:
        cv2.circle(frame, pt, r+3, (0,0,0), -1, cv2.LINE_AA)
        cv2.circle(frame, pt, r,   col,     -1, cv2.LINE_AA)

    txt(frame, f"{display_angle:.0f} deg", wrp[0]+14, wrp[1]-18,
        0.75, C_YELLOW, 2, bold=True)


# ═══════════════════════════════════════════════════════════
#  LIVE UI
# ═══════════════════════════════════════════════════════════

def draw_live(frame, pose_lm, hand_lm, raw_angle, display_angle,
              state, side, ok, msg, hint,
              max_flex, max_ext, max_rad, max_uln,
              countdown, stable, w, h):

    draw_hand_skeleton(frame, pose_lm, hand_lm, side, w, h, display_angle)

    # TOP BAR
    rrect(frame, 0, 0, w, 68, 0, (14,14,24), alpha=0.88)
    step_map = {
        TestState.NEUTRAL_BASELINE: ("STEP 1/5", "Hold wrist in NEUTRAL position — forearm and hand flat"),
        TestState.FLEXION_TEST:     ("STEP 2/5", "Perform WRIST FLEXION — bend as far as possible"),
        TestState.EXTENSION_TEST:   ("STEP 3/5", "Perform WRIST EXTENSION — extend as far as possible"),
        TestState.RADIAL_TEST:      ("STEP 4/5", "Perform RADIAL DEVIATION — deviate toward thumb side"),
        TestState.ULNAR_TEST:       ("STEP 5/5", "Perform ULNAR DEVIATION — deviate toward pinky side"),
    }
    sl, si_ = step_map.get(state, ("",""))
    txt(frame, sl,  14, 24, 0.52, C_CYAN,  1)
    txt(frame, si_, 14, 54, 0.62, C_WHITE, 1)

    # SIDE BADGE
    bc = (160,50,0) if side=='left' else (0,70,160)
    rrect(frame, w-128, 6, w-6, 62, 6, bc, alpha=0.92)
    txt(frame, f"{side.upper()} WRIST", w-118, 42, 0.58, C_WHITE, 2)

    # ARC GAUGE  — scale to the relevant max angle
    max_a_map = {
        TestState.NEUTRAL_BASELINE: 30,
        TestState.FLEXION_TEST:     THRESH['flexion_normal'],
        TestState.EXTENSION_TEST:   THRESH['extension_normal'],
        TestState.RADIAL_TEST:      THRESH['radial_normal'],
        TestState.ULNAR_TEST:       THRESH['ulnar_normal'],
    }
    gauge_max = max_a_map.get(state, 80)
    gcx, gcy = w-95, h-120
    arc_gauge(frame, gcx, gcy, 72, display_angle, max_a=gauge_max)
    txt(frame, f"{display_angle:.0f} deg", gcx-36, gcy+20, 0.82, C_YELLOW, 2, bold=True)
    txt(frame, "ANGLE",                   gcx-28, gcy+42, 0.44, C_WHITE,  1)

    # POSTURE FEEDBACK
    fy = h-105
    rrect(frame, 8, fy, 580, h-8, 8,
          (0,55,0) if ok else (55,0,0), alpha=0.78)
    fc = C_GREEN if ok else C_RED
    txt(frame, msg,  18, fy+30, 0.58, fc, 2)
    if hint:
        txt(frame, hint, 18, fy+58, 0.52, C_ORANGE, 1)

    # PROGRESS BARS (4 movements)
    rrect(frame, 8, 82, 230, 268, 8, (18,18,32), alpha=0.78)
    txt(frame, "PROGRESS", 18, 106, 0.48, C_CYAN, 1)
    bw = 195

    def _bar(label, val, ref, yy, good_fn):
        txt(frame, label, 18, yy, 0.41, C_WHITE, 1)
        fp_ = min(abs(val)/ref, 1.0) if ref > 0 else 0.0
        cv2.rectangle(frame, (18, yy+8),  (18+bw, yy+20), (50,50,50), -1)
        cv2.rectangle(frame, (18, yy+8),  (18+int(bw*fp_), yy+20),
                      C_GREEN if good_fn(val) else C_ORANGE, -1)

    _bar(f"Flexion:   {max_flex:.0f}/{THRESH['flexion_normal']} deg",
         max_flex, THRESH['flexion_normal'],   122, lambda v: v >= THRESH['flexion_min'])
    _bar(f"Extension: {max_ext:.0f}/{THRESH['extension_normal']} deg",
         max_ext,  THRESH['extension_normal'], 154, lambda v: v >= THRESH['extension_min'])
    _bar(f"Radial:    {max_rad:.0f}/{THRESH['radial_normal']} deg",
         max_rad,  THRESH['radial_normal'],    186, lambda v: v >= THRESH['radial_min'])
    _bar(f"Ulnar:     {max_uln:.0f}/{THRESH['ulnar_normal']} deg",
         max_uln,  THRESH['ulnar_normal'],     218, lambda v: v >= THRESH['ulnar_min'])

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

def draw_report(result: WristResult, secs_left: float):
    img = np.zeros((WINDOW_H, WINDOW_W, 3), dtype=np.uint8)
    img[:] = (16, 16, 26)

    # Header
    rrect(img, 0, 0, WINDOW_W, 56, 0, (28,28,46), alpha=1.0)
    txt(img, "WRIST ROM REPORT", WINDOW_W//2-140, 38,
        0.92, C_CYAN, 2, bold=True)
    txt(img, f"Closing in {secs_left:.0f}s",
        WINDOW_W-185, 38, 0.48, (130,130,130), 1)
    cv2.line(img, (28,60), (WINDOW_W-28,60), (55,55,75), 1)

    # Side badge
    sc = (160,50,0) if result.side=='left' else (0,70,160)
    rrect(img, 28, 68, 200, 106, 6, sc, alpha=0.92)
    txt(img, f"{result.side.upper()} WRIST", 40, 96, 0.64, C_WHITE, 2)
    txt(img, result.timestamp, WINDOW_W-340, 92, 0.40, (130,130,130), 1)

    y = 118
    cv2.line(img, (28,y), (WINDOW_W-28,y), (38,38,52), 1)
    y += 18
    txt(img, "MEASUREMENTS", 34, y+14, 0.56, C_CYAN, 1)
    y += 34

    def mrow(label, val, ref, ok_, yy):
        tag = "NORMAL" if ok_ else "REDUCED"
        tc  = C_GREEN   if ok_ else C_RED
        bg  = (24,38,24) if ok_ else (42,18,18)
        rrect(img, 32, yy-20, WINDOW_W-32, yy+10, 4, bg, alpha=0.88)
        txt(img, label,           38,           yy,  0.48, C_WHITE,       1)
        txt(img, f"{val:.0f} deg",310,           yy,  0.58, C_YELLOW,      2)
        txt(img, ref,             375,           yy,  0.41, (155,155,155), 1)
        txt(img, tag, WINDOW_W-128, yy,          0.50, tc,                 2)

    mrow("Flexion   (bend hand down)",
         result.max_flexion,
         f">= {THRESH['flexion_normal']} deg",
         result.flexion_normal,  y);  y += 34

    mrow("Extension (push hand up)",
         result.max_extension,
         f">= {THRESH['extension_normal']} deg",
         result.extension_normal, y); y += 34

    mrow("Radial Dev. (toward thumb)",
         result.max_radial,
         f">= {THRESH['radial_normal']} deg",
         result.radial_normal,   y);  y += 34

    mrow("Ulnar Dev.  (toward pinky)",
         result.max_ulnar,
         f">= {THRESH['ulnar_normal']} deg",
         result.ulnar_normal,    y);  y += 40

    # Flare-up risk row
    rrect(img, 32, y-18, WINDOW_W-32, y+12, 4, (26,26,46), alpha=0.88)
    txt(img, "Estimated flare-up risk", 38, y, 0.50, C_WHITE, 1)
    txt(img, f"{result.flare_risk:.0f}%", 310, y, 0.58, C_YELLOW, 2)
    bar_w = 245; bx1 = 375
    cv2.rectangle(img, (bx1, y-8), (bx1+bar_w, y+4), (50,50,50), -1)
    rw = int(bar_w * max(0.0, min(result.flare_risk, 100.0)) / 100.0)
    rc = C_GREEN if result.flare_risk < 25 else C_ORANGE
    cv2.rectangle(img, (bx1, y-8), (bx1+rw, y+4), rc, -1)
    y += 38

    cv2.line(img, (28,y), (WINDOW_W-28,y), (38,38,52), 1); y += 16

    # Clinical summary
    txt(img, "CLINICAL SUMMARY", 34, y+14, 0.56, C_CYAN, 1); y += 34
    overall_ok = (result.flexion_normal and result.extension_normal
                  and result.radial_normal and result.ulnar_normal)

    if overall_ok:
        bg2  = (18,44,18); lc = C_GREEN
        lines = [
            "  Wrist ROM within AAOS normal limits.",
            f"  Flex {result.max_flexion:.0f}  |  Ext {result.max_extension:.0f}"
            f"  |  Rad {result.max_radial:.0f}  |  Uln {result.max_ulnar:.0f}  (deg)"
        ]
    else:
        bg2  = (44,18,18); lc = C_ORANGE
        lines = []
        if not result.flexion_normal:
            lines.append(f"  Flexion restricted – {result.max_flexion:.0f} deg "
                         f"(deficit {result.flexion_deficit:.0f} deg below {THRESH['flexion_normal']} deg)")
        if not result.extension_normal:
            lines.append(f"  Extension restricted – {result.max_extension:.0f} deg "
                         f"(deficit {result.extension_deficit:.0f} deg below {THRESH['extension_normal']} deg)")
        if not result.radial_normal:
            lines.append(f"  Radial deviation reduced – {result.max_radial:.0f} deg "
                         f"(deficit {result.radial_deficit:.0f} deg below {THRESH['radial_normal']} deg)")
        if not result.ulnar_normal:
            lines.append(f"  Ulnar deviation reduced – {result.max_ulnar:.0f} deg "
                         f"(deficit {result.ulnar_deficit:.0f} deg below {THRESH['ulnar_normal']} deg)")

    rrect(img, 32, y-4, WINDOW_W-32, y+len(lines)*28+10, 6, bg2, alpha=0.88)
    for ln in lines:
        txt(img, ln, 42, y+20, 0.46, lc, 1); y += 28
    y += 18

    cv2.line(img, (28,y), (WINDOW_W-28,y), (38,38,52), 1); y += 14

    # Recommendations
    txt(img, "RECOMMENDATIONS", 34, y+14, 0.56, C_CYAN, 1); y += 32
    if not result.abnormalities:
        recs = ["Continue current exercise and treatment plan.",
                "Routine wrist ROM check every 3 months."]
        rc2 = C_GREEN
    else:
        recs = ["Clinical evaluation recommended — ROM restriction detected.",
                "Consider rheumatology / physiotherapy consultation.",
                "Wrist ROM exercises: 3x daily, 10 reps each direction.",
                "Splinting at night and heat therapy may improve flexibility."]
        rc2 = (200, 175, 100)
    for r in recs:
        txt(img, f"  {r}", 42, y+18, 0.46, rc2, 1); y += 26

    # Progress bar
    bw2 = WINDOW_W - 40
    bf2 = int(bw2 * (secs_left / REPORT_SECS))
    cv2.rectangle(img, (20, WINDOW_H-10), (20+bw2, WINDOW_H-3), (45,45,45), -1)
    cv2.rectangle(img, (20, WINDOW_H-10), (20+bf2,  WINDOW_H-3), C_CYAN,    -1)

    return img


# ═══════════════════════════════════════════════════════════
#  CLINICAL ANALYSIS  — risk score (same style as elbow)
# ═══════════════════════════════════════════════════════════

def analyze(neutral, max_flex, max_ext, max_rad, max_uln, side) -> WristResult:
    neutral  = float(neutral)
    max_flex = float(max_flex)
    max_ext  = float(max_ext)
    max_rad  = float(max_rad)
    max_uln  = float(max_uln)

    flex_ok = max_flex >= THRESH['flexion_min']
    ext_ok  = max_ext  >= THRESH['extension_min']
    rad_ok  = max_rad  >= THRESH['radial_min']
    uln_ok  = max_uln  >= THRESH['ulnar_min']

    fd  = max(0.0, THRESH['flexion_normal']   - max_flex)
    ed  = max(0.0, THRESH['extension_normal'] - max_ext)
    rd  = max(0.0, THRESH['radial_normal']    - max_rad)
    ud  = max(0.0, THRESH['ulnar_normal']     - max_uln)

    # ── Heuristic flare-up risk ──
    nsev  = min(max(0.0, neutral  - THRESH['neutral_max']) / 10.0, 1.0)
    fsev  = min(fd / 30.0, 1.0) if not flex_ok else 0.0
    esev  = min(ed / 25.0, 1.0) if not ext_ok  else 0.0
    rsev  = min(rd / 10.0, 1.0) if not rad_ok  else 0.0
    usev  = min(ud / 15.0, 1.0) if not uln_ok  else 0.0

    # Weights: flexion 30 %, extension 25 %, radial 15 %, ulnar 20 %, neutral 10 %
    sev_score = 0.10*nsev + 0.30*fsev + 0.25*esev + 0.15*rsev + 0.20*usev
    flare_risk = float(min(95.0, 5.0 + sev_score * 90.0))

    abn = []
    if not flex_ok:
        abn.append(
            f"Reduced flexion: {max_flex:.0f} deg "
            f"(normal >= {THRESH['flexion_normal']} deg, deficit {fd:.0f} deg)"
        )
    if not ext_ok:
        abn.append(
            f"Reduced extension: {max_ext:.0f} deg "
            f"(normal >= {THRESH['extension_normal']} deg, deficit {ed:.0f} deg)"
        )
    if not rad_ok:
        abn.append(
            f"Reduced radial deviation: {max_rad:.0f} deg "
            f"(normal >= {THRESH['radial_normal']} deg, deficit {rd:.0f} deg)"
        )
    if not uln_ok:
        abn.append(
            f"Reduced ulnar deviation: {max_uln:.0f} deg "
            f"(normal >= {THRESH['ulnar_normal']} deg, deficit {ud:.0f} deg)"
        )

    if flare_risk < 25:
        risk_level = "low"
        suggestions = [
            "Maintain current exercise and treatment plan.",
            "Routine wrist ROM check every 3–6 months.",
        ]
    elif flare_risk < 60:
        risk_level = "moderate"
        suggestions = [
            "Increase supervised wrist ROM and strengthening exercises.",
            "Monitor for pain, swelling or morning stiffness in the wrist.",
            "Discuss disease control and physiotherapy at next clinic visit.",
        ]
    else:
        risk_level = "high"
        suggestions = [
            "Clinical review recommended – restricted ROM with higher flare-up risk.",
            "Consider rheumatology / physiotherapy consultation soon.",
            "Short-term increase in wrist ROM and stretching exercises as tolerated.",
            "Monitor closely for flare symptoms (pain, swelling, warmth, limited grip).",
        ]

    return WristResult(
        side=side,
        neutral_angle=neutral,
        max_flexion=max_flex,
        max_extension=max_ext,
        max_radial=max_rad,
        max_ulnar=max_uln,
        flexion_normal=bool(flex_ok),
        extension_normal=bool(ext_ok),
        radial_normal=bool(rad_ok),
        ulnar_normal=bool(uln_ok),
        flexion_deficit=fd,
        extension_deficit=ed,
        radial_deficit=rd,
        ulnar_deficit=ud,
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

    jp = os.path.join(folder, f"{pid}_wrist_report.json")
    with open(jp, 'w') as f:
        json.dump(
            {
                'patient_id':   pid,
                'test_date':    ts,
                'left_wrist':   asdict(left_r)  if left_r  else None,
                'right_wrist':  asdict(right_r) if right_r else None,
                'reference': {
                    'flexion_normal_deg':   THRESH['flexion_normal'],
                    'extension_normal_deg': THRESH['extension_normal'],
                    'radial_normal_deg':    THRESH['radial_normal'],
                    'ulnar_normal_deg':     THRESH['ulnar_normal'],
                    'source': 'AAOS (American Academy of Orthopaedic Surgeons)',
                    'notes': (
                        "ROM thresholds based on AAOS clinical standards; "
                        "flare-up risk is a heuristic score from ROM deficits and is "
                        "NOT a formal medical diagnosis."
                    ),
                },
            },
            f, indent=2,
        )

    tp = os.path.join(folder, f"{pid}_wrist_summary.txt")
    with open(tp, 'w', encoding='utf-8') as f:
        sep = "=" * 60
        f.write(sep + "\n  WRIST ROM ANALYSIS REPORT\n" + sep + "\n")
        f.write(f"  Patient ID : {pid}\n  Test Date  : {ts}\n" + sep + "\n\n")
        for res, lbl in [(left_r, "LEFT WRIST"), (right_r, "RIGHT WRIST")]:
            if not res: continue
            f.write(f"  {lbl}\n  " + "─"*44 + "\n")
            f.write(f"  Neutral      : {res.neutral_angle:.1f} deg  (normal <= {THRESH['neutral_max']} deg)\n")
            f.write(
                f"  Max Flexion  : {res.max_flexion:.1f} deg  (normal >= {THRESH['flexion_normal']} deg)"
                f"  -> {'NORMAL' if res.flexion_normal else f'REDUCED deficit {res.flexion_deficit:.0f} deg'}\n"
            )
            f.write(
                f"  Max Extension: {res.max_extension:.1f} deg  (normal >= {THRESH['extension_normal']} deg)"
                f"  -> {'NORMAL' if res.extension_normal else f'REDUCED deficit {res.extension_deficit:.0f} deg'}\n"
            )
            f.write(
                f"  Radial Dev.  : {res.max_radial:.1f} deg  (normal >= {THRESH['radial_normal']} deg)"
                f"  -> {'NORMAL' if res.radial_normal else f'REDUCED deficit {res.radial_deficit:.0f} deg'}\n"
            )
            f.write(
                f"  Ulnar Dev.   : {res.max_ulnar:.1f} deg  (normal >= {THRESH['ulnar_normal']} deg)"
                f"  -> {'NORMAL' if res.ulnar_normal else f'REDUCED deficit {res.ulnar_deficit:.0f} deg'}\n"
            )
            all_ok = res.flexion_normal and res.extension_normal and res.radial_normal and res.ulnar_normal
            f.write(f"  Flare-up risk: {res.flare_risk:.0f}%  ({res.risk_level.upper()} risk, heuristic)\n")
            f.write(f"  Status       : {'NORMAL' if all_ok else 'RESTRICTION DETECTED'}\n")
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
        f.write("  RECOMMENDATIONS\n  " + "─"*44 + "\n")
        if not issues:
            f.write("  - Wrist ROM normal. Continue treatment plan.\n")
            f.write("  - Routine monitoring every 3 months.\n")
        else:
            f.write("  - Clinical evaluation recommended.\n")
            f.write("  - Consider rheumatology / physiotherapy referral.\n")
            f.write("  - Wrist ROM exercises 3x daily, 10 reps each direction.\n")
            f.write("  - Night splinting and heat therapy may help.\n")
        f.write("\n  AAOS REFERENCE\n  " + "─"*44 + "\n")
        f.write(f"  Flexion         : >= {THRESH['flexion_normal']} deg\n")
        f.write(f"  Extension       : >= {THRESH['extension_normal']} deg\n")
        f.write(f"  Radial Deviation: >= {THRESH['radial_normal']} deg\n")
        f.write(f"  Ulnar Deviation : >= {THRESH['ulnar_normal']} deg\n" + sep + "\n")

    print(f"  TXT report  -> {tp}")
    print(f"  JSON report -> {jp}")


# ═══════════════════════════════════════════════════════════
#  SINGLE-WRIST ANALYZER  (mirrors ArmAnalyzer / ShoulderAnalyzer)
# ═══════════════════════════════════════════════════════════

class WristAnalyzer:
    def __init__(self, cap, pose, hands, side: str, pid: str, out: str):
        self.cap   = cap
        self.pose  = pose
        self.hands = hands
        self.side  = side
        self.pid   = pid
        self.out   = out

        self.state    = TestState.NEUTRAL_BASELINE
        self.neutral  = 0.0
        self.max_flex = 0.0
        self.max_ext  = 0.0
        self.max_rad  = 0.0
        self.max_uln  = 0.0

        self.buf    = deque(maxlen=STAB['buf'])
        self.hist   = []
        self.hold_t = None

    # ── value shown in UI and gauge (always positive) ──
    @staticmethod
    def _display(raw: float, state: TestState) -> float:
        if state == TestState.FLEXION_TEST:
            return max(0.0, -raw)
        return abs(raw)

    def _stable(self, a: float) -> bool:
        self.buf.append(a)
        if len(self.buf) < STAB['buf']:
            return False
        return float(np.var(list(self.buf))) < STAB['variance']

    def _plateaued(self, a: float) -> bool:
        if self.state == TestState.NEUTRAL_BASELINE:
            return True
        self.hist.append(abs(a))
        if len(self.hist) > STAB['plateau_win']:
            self.hist.pop(0)
        if len(self.hist) < STAB['plateau_win']:
            return False
        return abs(float(np.mean(self.hist[-5:])) -
                   float(np.mean(self.hist[:5]))) < STAB['plateau_tol']

    def _capture(self, frame, display_a: float):
        cv2.imwrite(
            os.path.join(self.out, f"{self.pid}_{self.side}_{self.state.name}.jpg"),
            frame
        )
        if   self.state == TestState.NEUTRAL_BASELINE:
            self.neutral = display_a;  self.state = TestState.FLEXION_TEST
        elif self.state == TestState.FLEXION_TEST:
                                       self.state = TestState.EXTENSION_TEST
        elif self.state == TestState.EXTENSION_TEST:
                                       self.state = TestState.RADIAL_TEST
        elif self.state == TestState.RADIAL_TEST:
                                       self.state = TestState.ULNAR_TEST
        elif self.state == TestState.ULNAR_TEST:
                                       self.state = TestState.COMPLETE
        self.hold_t = None;  self.buf.clear();  self.hist.clear()

    def run(self) -> Optional[WristResult]:
        while True:
            ret, raw = self.cap.read()
            if not ret:
                break

            raw   = cv2.flip(raw, 1)
            frame = cv2.resize(raw, (WINDOW_W, WINDOW_H))
            rgb   = cv2.cvtColor(frame, cv2.COLOR_BGR2RGB)

            pose_res  = self.pose.process(rgb)
            hand_res  = self.hands.process(rgb)

            # ── COMPLETE → show 12 s report ──
            if self.state == TestState.COMPLETE:
                result   = analyze(self.neutral, self.max_flex, self.max_ext,
                                   self.max_rad,  self.max_uln, self.side)
                deadline = time.time() + REPORT_SECS
                while time.time() < deadline:
                    sl = deadline - time.time()
                    cv2.imshow("Wrist ROM Analyzer", draw_report(result, sl))
                    if (cv2.waitKey(40) & 0xFF) != 255:
                        break
                return result

            # ── ACTIVE ──
            if pose_res.pose_landmarks and hand_res.multi_hand_landmarks:
                pose_lm = pose_res.pose_landmarks.landmark
                hand_lm = hand_res.multi_hand_landmarks[0]   # full landmark object

                forearm_2d, flex_hand, dev_hand, knuckle, wrist_px, elbow_px, vis = \
                    get_wrist_vectors(pose_lm, hand_lm.landmark, self.side)

                if vis > 0.35 and np.linalg.norm(forearm_2d) > 0.015:
                    raw_angle = compute_wrist_angle(
                        forearm_2d, flex_hand, dev_hand, knuckle, self.state)
                    disp_a = self._display(raw_angle, self.state)

                    # accumulate maxima
                    if self.state == TestState.FLEXION_TEST:
                        self.max_flex = max(self.max_flex, max(0.0, -raw_angle))
                    elif self.state == TestState.EXTENSION_TEST:
                        self.max_ext  = max(self.max_ext,  max(0.0,  raw_angle))
                    elif self.state == TestState.RADIAL_TEST:
                        self.max_rad  = max(self.max_rad,  abs(raw_angle))
                    elif self.state == TestState.ULNAR_TEST:
                        self.max_uln  = max(self.max_uln,  abs(raw_angle))

                    ok, msg, hint = validate(raw_angle, self.state)
                    stable        = self._stable(raw_angle)
                    plateaued     = self._plateaued(raw_angle)
                    ready         = ok and stable and plateaued

                    cd = None
                    if ready:
                        if self.hold_t is None:
                            self.hold_t = time.time()
                        rem = STAB['hold_sec'] - (time.time() - self.hold_t)
                        cd  = max(0.0, rem)
                        if rem <= 0:
                            cap_frame = frame.copy()
                            draw_hand_skeleton(cap_frame, pose_lm, hand_lm,
                                               self.side, WINDOW_W, WINDOW_H, disp_a)
                            self._capture(cap_frame, disp_a)
                            continue
                    else:
                        self.hold_t = None

                    frame = draw_live(
                        frame, pose_lm, hand_lm, raw_angle, disp_a,
                        self.state, self.side, ok, msg, hint,
                        self.max_flex, self.max_ext, self.max_rad, self.max_uln,
                        cd, stable, WINDOW_W, WINDOW_H
                    )
                else:
                    rrect(frame, 8, WINDOW_H-68, 560, WINDOW_H-8, 6, (55,0,0), 0.8)
                    txt(frame, "Hold wrist closer / improve lighting",
                        18, WINDOW_H-30, 0.58, C_RED, 2)
            else:
                rrect(frame, 8, WINDOW_H-68, 580, WINDOW_H-8, 6, (55,0,0), 0.8)
                txt(frame, "Hand / body not detected — step back and show hand",
                    18, WINDOW_H-30, 0.55, C_RED, 2)

            cv2.imshow("Wrist ROM Analyzer", frame)
            k = cv2.waitKey(10) & 0xFF
            if k == ord('q'):
                return None
            elif k == ord('r'):
                self.state    = TestState.NEUTRAL_BASELINE
                self.neutral  = 0.0
                self.max_flex = 0.0
                self.max_ext  = 0.0
                self.max_rad  = 0.0
                self.max_uln  = 0.0
                self.buf.clear()
                self.hist.clear()
                self.hold_t = None

        return None


# ═══════════════════════════════════════════════════════════
#  MAIN
# ═══════════════════════════════════════════════════════════

def main():
    print("\n" + "═"*52)
    print("  WRIST ROM ANALYZER")
    print("  Rheumatoid Arthritis Detection")
    print("  AAOS Clinical Standards")
    print("═"*52)

    pid = input("\n  Patient ID (e.g. P001): ").strip()
    if not pid:
        print("  Patient ID required."); return

    cam = select_camera()
    out = os.path.join("wrist_output", pid)
    os.makedirs(out, exist_ok=True)

    print("\n  Connecting to camera...")
    cap = cv2.VideoCapture(cam)
    if not cap.isOpened():
        print(f"  Cannot open camera: {cam}"); return
    ret, _ = cap.read()
    if not ret:
        print("  Camera open but no frames."); cap.release(); return
    print("  Camera ready\n")

    # Shared MediaPipe models
    pose = mp.solutions.pose.Pose(
        static_image_mode=False, model_complexity=1,
        min_detection_confidence=0.65, min_tracking_confidence=0.65)

    hands = mp.solutions.hands.Hands(
        static_image_mode=False, max_num_hands=1,
        min_detection_confidence=0.65, min_tracking_confidence=0.65)

    cv2.namedWindow("Wrist ROM Analyzer", cv2.WINDOW_NORMAL)
    cv2.resizeWindow("Wrist ROM Analyzer", WINDOW_W, WINDOW_H)

    print("  Starting LEFT wrist test...")
    left_r = WristAnalyzer(cap, pose, hands, 'left',  pid+"_L", out).run()

    right_r = None
    if left_r is not None:
        print("  Left done — starting RIGHT wrist test...")
        time.sleep(1.2)
        right_r = WristAnalyzer(cap, pose, hands, 'right', pid+"_R", out).run()

    if left_r or right_r:
        save_report(out, pid, left_r, right_r)
        print(f"\n  All files saved: wrist_output/{pid}/")

    cap.release()
    pose.close()
    hands.close()
    cv2.destroyAllWindows()
    print("  Test complete.\n")


if __name__ == "__main__":
    main()