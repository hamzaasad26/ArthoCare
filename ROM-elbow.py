import cv2
import numpy as np
from collections import deque
import mediapipe as mp
from mediapipe.tasks import python
from mediapipe.tasks.python import vision


# ---------------------------
# Config
# ---------------------------
SIDE = "left"  # "right" or "left"
AUTO_CALIB_THRESHOLD_MIN = 85   # minimum angle for 90° calibration
AUTO_CALIB_THRESHOLD_MAX = 95   # maximum angle for 90° calibration
AUTO_CALIB_HOLD_FRAMES = 10
SMOOTHING_WINDOW = 5

# ---------------------------
# Model setup (Pose Landmarker v2)
# ---------------------------
model_path = "pose_landmarker_full.task"  # Download from: https://developers.google.com/mediapipe/solutions/vision/pose_landmarker

BaseOptions = python.BaseOptions
PoseLandmarker = vision.PoseLandmarker
PoseLandmarkerOptions = vision.PoseLandmarkerOptions
VisionRunningMode = vision.RunningMode

base_options = BaseOptions(model_asset_path=model_path)
options = PoseLandmarkerOptions(
    base_options=base_options,
    running_mode=VisionRunningMode.VIDEO,
    output_segmentation_masks=False
)

landmarker = PoseLandmarker.create_from_options(options)

# ---------------------------
# Setup capture
# ---------------------------
cap = cv2.VideoCapture(0)
neutral_angle = None
angle_history = deque(maxlen=SMOOTHING_WINDOW)
calib_counter = 0
timestamp = 0

# ---------------------------
# Helper: Calculate joint angle
# ---------------------------
def calculate_angle(p1, p2, p3):
    v1 = p1 - p2
    v2 = p3 - p2
    cosine_angle = np.dot(v1, v2) / (np.linalg.norm(v1) * np.linalg.norm(v2))
    cosine_angle = np.clip(cosine_angle, -1.0, 1.0)
    return np.degrees(np.arccos(cosine_angle))

# ---------------------------
# Helper: Apply lighting compensation
# ---------------------------
def enhance_lighting(frame):
    """
    Apply multiple techniques to handle strong lighting and improve detection
    """
    # Method 1: CLAHE (Contrast Limited Adaptive Histogram Equalization)
    lab = cv2.cvtColor(frame, cv2.COLOR_BGR2LAB)
    l_channel, a, b = cv2.split(lab)
    clahe = cv2.createCLAHE(clipLimit=3.0, tileGridSize=(8, 8))
    l_channel_enhanced = clahe.apply(l_channel)
    lab_enhanced = cv2.merge([l_channel_enhanced, a, b])
    enhanced = cv2.cvtColor(lab_enhanced, cv2.COLOR_LAB2BGR)
    
    # Method 2: Gamma correction (brightness adjustment)
    gamma = 0.8  # < 1 for brightening, > 1 for darkening
    inv_gamma = 1.0 / gamma
    table = np.array([((i / 255.0) ** inv_gamma) * 255 for i in np.arange(0, 256)]).astype("uint8")
    gamma_corrected = cv2.LUT(enhanced, table)
    
    # Method 3: Bilateral filter to smooth while preserving edges
    bilateral = cv2.bilateralFilter(gamma_corrected, 5, 50, 50)
    
    # Method 4: Shadow/Highlight correction (simple approach)
    lab_bilateral = cv2.cvtColor(bilateral, cv2.COLOR_BGR2LAB)
    l_b, a_b, b_b = cv2.split(lab_bilateral)
    
    # Clamp very bright areas
    l_b = np.clip(l_b * 0.95, 0, 255).astype(np.uint8)
    
    # Clamp very dark areas
    l_b = np.clip(l_b * 1.1 + 10, 0, 255).astype(np.uint8)
    
    lab_final = cv2.merge([l_b, a_b, b_b])
    final_enhanced = cv2.cvtColor(lab_final, cv2.COLOR_LAB2BGR)
    
    return final_enhanced

# ---------------------------
# Main loop
# ---------------------------
while cap.isOpened():
    ret, frame = cap.read()
    if not ret:
        break

    frame = cv2.flip(frame, 1)
    h, w, _ = frame.shape
    
    # Apply lighting compensation before processing
    enhanced_frame = enhance_lighting(frame)
    
    # Convert to RGB for MediaPipe
    rgb = cv2.cvtColor(enhanced_frame, cv2.COLOR_BGR2RGB)

    # Process video frame
    mp_image = mp.Image(image_format=mp.ImageFormat.SRGB, data=rgb)
    result = landmarker.detect_for_video(mp_image, timestamp)
    timestamp += 33  # ~30 FPS

    if result.pose_landmarks:
        landmarks = result.pose_landmarks[0]  # first person

        # Select correct side
        if SIDE.lower().startswith("r"):
            shoulder = landmarks[12]
            elbow = landmarks[14]
            wrist = landmarks[16]
        else:
            shoulder = landmarks[11]
            elbow = landmarks[13]
            wrist = landmarks[15]

        # ---- 3D coordinates ----
        p1 = np.array([shoulder.x, shoulder.y, shoulder.z])
        p2 = np.array([elbow.x, elbow.y, elbow.z])
        p3 = np.array([wrist.x, wrist.y, wrist.z])

        raw_angle = calculate_angle(p1, p2, p3)
        angle_history.append(raw_angle)
        smooth_angle = np.mean(angle_history)

        # ---- Auto-calibrate when at 90° (perpendicular) ----
        if AUTO_CALIB_THRESHOLD_MIN <= smooth_angle <= AUTO_CALIB_THRESHOLD_MAX:
            calib_counter += 1
        else:
            calib_counter = 0

        if calib_counter >= AUTO_CALIB_HOLD_FRAMES and neutral_angle is None:
            neutral_angle = smooth_angle
            print(f"✅ Auto-calibrated! Neutral angle (90°) set at {neutral_angle:.2f}°")

        # ---- Determine phase based on angle relative to neutral ----
        if neutral_angle:
            # Angle larger than 90° = Extended (Green)
            # Angle smaller than 90° = Flexed (Red)
            angle_diff = smooth_angle - neutral_angle
            
            if angle_diff > 30:  # Extended by 30+ degrees
                phase, color = "Extended", (0, 255, 0)  # Green
            elif angle_diff < -30:  # Flexed by 30+ degrees
                phase, color = "Flexed", (0, 0, 255)  # Red
            else:
                phase, color = "Neutral", (255, 255, 255)  # White
        else:
            # Before calibration, use raw angle estimates
            if smooth_angle > 120:
                phase, color = "Extended", (0, 255, 0)  # Green
            elif smooth_angle < 60:
                phase, color = "Flexed", (0, 0, 255)  # Red
            else:
                phase, color = "Neutral", (255, 255, 255)  # White

        # ---- Draw on original frame for display ----
        for point in [shoulder, elbow, wrist]:
            px, py = int(point.x * w), int(point.y * h)
            cv2.circle(frame, (px, py), 8, color, -1)

        cv2.line(frame, 
                 (int(shoulder.x * w), int(shoulder.y * h)), 
                 (int(elbow.x * w), int(elbow.y * h)), 
                 (255, 255, 255), 2)
        cv2.line(frame, 
                 (int(elbow.x * w), int(elbow.y * h)), 
                 (int(wrist.x * w), int(wrist.y * h)), 
                 (255, 255, 255), 2)

        # ---- Info overlay ----
        cv2.putText(frame, f"Raw Angle: {smooth_angle:.1f} deg", (30, 50),
                    cv2.FONT_HERSHEY_SIMPLEX, 0.8, (255, 255, 255), 2)
        cv2.putText(frame, f"Phase: {phase}", (30, 80),
                    cv2.FONT_HERSHEY_SIMPLEX, 0.8, color, 2)
        if neutral_angle:
            cv2.putText(frame, f"Neutral: {neutral_angle:.1f} deg", (30, 110),
                        cv2.FONT_HERSHEY_SIMPLEX, 0.7, (200, 200, 200), 2)
            cv2.putText(frame, f"Diff: {smooth_angle - neutral_angle:+.1f} deg", (30, 140),
                        cv2.FONT_HERSHEY_SIMPLEX, 0.7, (200, 200, 200), 2)
    
    # Display original frame (not distorted)
    cv2.imshow("Elbow ROM Tracker", frame)
    
    # Optional: Uncomment to see the enhanced frame for debugging
    # cv2.imshow("Enhanced Processing", enhanced_frame)

    if cv2.waitKey(10) & 0xFF == ord('q'):
        break

cap.release()
cv2.destroyAllWindows()

