import cv2
import numpy as np
from collections import deque
import mediapipe as mp
import time

# ---------------------------
# Config
# ---------------------------
SIDE = "left"  # "left" or "right"
# Auto-calibration for wrist neutral (0 deg). Trigger when within [-5, +5] deg for 10 frames.
AUTO_CALIB_THRESHOLD_MIN = -5
AUTO_CALIB_THRESHOLD_MAX = 5
AUTO_CALIB_HOLD_FRAMES = 10
SMOOTHING_WINDOW = 5
PEAK_SNAPSHOT = False

# ---------------------------
# Mediapipe setup

# ---------------------------
mp_hands = mp.solutions.hands
mp_pose = mp.solutions.pose
mp_drawing = mp.solutions.drawing_utils

hands = mp_hands.Hands(
    static_image_mode=False,
    max_num_hands=1,
    min_detection_confidence=0.7,
    min_tracking_confidence=0.7
)

pose = mp_pose.Pose(
    static_image_mode=False,
    min_detection_confidence=0.7,
    min_tracking_confidence=0.7
)

# ---------------------------
# Capture setup
# ---------------------------
cap = cv2.VideoCapture(0)
angle_history = deque(maxlen=SMOOTHING_WINDOW)
neutral_angle = None
calib_counter = 0

# Trackers (kept for potential later use; not shown in UI for elbow-like design)
max_extension = 0
max_flexion = 0
snapshot_taken = False

# ---------------------------
# Main loop
# ---------------------------
while cap.isOpened():
    ret, frame = cap.read()
    if not ret:
        break

    frame = cv2.flip(frame, 1)
    rgb = cv2.cvtColor(frame, cv2.COLOR_BGR2RGB)

    pose_result = pose.process(rgb)
    hand_result = hands.process(rgb)

    if pose_result.pose_landmarks and hand_result.multi_hand_landmarks:
        pose_lm = pose_result.pose_landmarks.landmark

        if SIDE.lower().startswith("l"):
            shoulder = np.array([pose_lm[11].x, pose_lm[11].y, pose_lm[11].z])
            elbow = np.array([pose_lm[13].x, pose_lm[13].y, pose_lm[13].z])
            wrist_p = np.array([pose_lm[15].x, pose_lm[15].y, pose_lm[15].z])
        else:
            shoulder = np.array([pose_lm[12].x, pose_lm[12].y, pose_lm[12].z])
            elbow = np.array([pose_lm[14].x, pose_lm[14].y, pose_lm[14].z])
            wrist_p = np.array([pose_lm[16].x, pose_lm[16].y, pose_lm[16].z])

        # ---------------------------
        # Hand landmarks
        # ---------------------------
        hand_landmarks = hand_result.multi_hand_landmarks[0]
        lm = hand_landmarks.landmark
        index_mcp = np.array([lm[5].x, lm[5].y, lm[5].z])
        pinky_mcp = np.array([lm[17].x, lm[17].y, lm[17].z])
        hand_wrist = np.array([lm[0].x, lm[0].y, lm[0].z])

        # Compute in image plane (2D) to better capture visible flex/extension
        forearm_vec = (wrist_p[:2] - elbow[:2])
        wrist_vec = (pinky_mcp[:2] - hand_wrist[:2])  # wrist -> pinky direction

        # ---------------------------
        # Compute signed wrist angle
        # ---------------------------
        dot = float(np.dot(forearm_vec, wrist_vec))
        denom = float(np.linalg.norm(forearm_vec) * np.linalg.norm(wrist_vec) + 1e-8)
        cosang = np.clip(dot / denom, -1.0, 1.0)
        angle = np.degrees(np.arccos(cosang))
        # 2D cross product z-component for sign
        cross_z = forearm_vec[0] * wrist_vec[1] - forearm_vec[1] * wrist_vec[0]
        sign = np.sign(cross_z)
        signed_angle = angle * sign

        angle_history.append(signed_angle)
        smooth_angle = np.mean(angle_history)

        # ---------------------------
        # Auto-calibration (neutral at ~0 deg)
        # ---------------------------
        if neutral_angle is None:
            if AUTO_CALIB_THRESHOLD_MIN <= smooth_angle <= AUTO_CALIB_THRESHOLD_MAX:
                calib_counter += 1
            else:
                calib_counter = 0
            # No UI text during calibration (keep UI minimal)
            if calib_counter >= AUTO_CALIB_HOLD_FRAMES:
                neutral_angle = smooth_angle
                print(f"✅ Auto-calibrated! Neutral wrist set at {neutral_angle:.2f} deg")
                time.sleep(0.3)
        
        # ---------------------------
        # Phase detection and drawing (Elbow prot5-like design)
        # ---------------------------
        if neutral_angle is not None:
            angle_diff = smooth_angle - neutral_angle
            if angle_diff > 30:
                phase, phase_color = "Extended", (0, 255, 0)  # Green
            elif angle_diff < -30:
                phase, phase_color = "Flexed", (0, 0, 255)  # Red
            else:
                phase, phase_color = "Neutral", (255, 255, 255)  # White
        else:
            if smooth_angle > 30:
                phase, phase_color = "Extended", (0, 255, 0)
            elif smooth_angle < -30:
                phase, phase_color = "Flexed", (0, 0, 255)
            else:
                phase, phase_color = "Neutral", (255, 255, 255)

        # Minimal UI: only Angle and Phase
        if neutral_angle is not None:
            display_angle = smooth_angle - neutral_angle
        else:
            display_angle = smooth_angle
        cv2.putText(frame, f"Angle: {display_angle:+.1f} deg", (30, 50),
                    cv2.FONT_HERSHEY_SIMPLEX, 0.9, (255, 255, 255), 2)
        cv2.putText(frame, f"Phase: {phase}", (30, 85),
                    cv2.FONT_HERSHEY_SIMPLEX, 0.9, phase_color, 2)

    cv2.imshow("Wrist ROM Tracker (0 deg Neutral)", frame)
    if cv2.waitKey(10) & 0xFF == ord('q'):
        break

cap.release()
cv2.destroyAllWindows()
