"""
Hand Joint ROM Analysis for Rheumatoid Arthritis Detection
Using MediaPipe Hand Tracking

This system performs three clinical tests:
1. Finger Flexion/Extension (Fist Test)
2. Finger Abduction/Adduction (Spread Test)
3. Thumb Opposition/Pinch Test

Author: Hand ROM Analysis System
Date: 2025-12-04
"""

import cv2
import mediapipe as mp
import numpy as np
import time
import json
import os
from dataclasses import dataclass, asdict
from typing import List, Dict, Tuple, Optional
from enum import Enum
from collections import deque
import math


# ==================== CONFIGURATION ====================

class TestState(Enum):
    """States for the test sequence"""
    WAITING = 0
    OPEN_HAND_BASELINE = 1
    FIST_TEST = 2
    OPEN_HAND_RETURN = 3
    SPREAD_TEST = 4
    CLOSE_HAND_RETURN = 5
    THUMB_OPPOSITION_INDEX = 6
    THUMB_OPPOSITION_MIDDLE = 7
    THUMB_OPPOSITION_RING = 8
    THUMB_OPPOSITION_PINKY = 9
    COMPLETE = 10


# Normal ROM thresholds (in degrees and mm)
# Normal ROM thresholds (in degrees and mm)
NORMAL_THRESHOLDS = {
    'mcp_flexion': 50,      # degrees (Relaxed from 85)
    'pip_flexion': 80,      # degrees (Relaxed from 100)
    'dip_flexion': 50,      # degrees (Relaxed from 80)
    'finger_abduction': 15, # degrees between adjacent fingers (Relaxed from 20)
    'tip_to_mcp_max': 0.30, # normalized distance (Relaxed from 0.15)
    'opposition_distance': 0.12  # normalized distance (MUST match validation: 0.12)
}

# MediaPipe Hand Landmark Indices
class HandLandmark:
    WRIST = 0
    THUMB_CMC = 1
    THUMB_MCP = 2
    THUMB_IP = 3
    THUMB_TIP = 4
    INDEX_MCP = 5
    INDEX_PIP = 6
    INDEX_DIP = 7
    INDEX_TIP = 8
    MIDDLE_MCP = 9
    MIDDLE_PIP = 10
    MIDDLE_DIP = 11
    MIDDLE_TIP = 12
    RING_MCP = 13
    RING_PIP = 14
    RING_DIP = 15
    RING_TIP = 16
    PINKY_MCP = 17
    PINKY_PIP = 18
    PINKY_DIP = 19
    PINKY_TIP = 20


# Finger landmark groups
FINGER_LANDMARKS = {
    'thumb': [HandLandmark.THUMB_CMC, HandLandmark.THUMB_MCP, 
              HandLandmark.THUMB_IP, HandLandmark.THUMB_TIP],
    'index': [HandLandmark.INDEX_MCP, HandLandmark.INDEX_PIP, 
              HandLandmark.INDEX_DIP, HandLandmark.INDEX_TIP],
    'middle': [HandLandmark.MIDDLE_MCP, HandLandmark.MIDDLE_PIP, 
               HandLandmark.MIDDLE_DIP, HandLandmark.MIDDLE_TIP],
    'ring': [HandLandmark.RING_MCP, HandLandmark.RING_PIP, 
             HandLandmark.RING_DIP, HandLandmark.RING_TIP],
    'pinky': [HandLandmark.PINKY_MCP, HandLandmark.PINKY_PIP, 
              HandLandmark.PINKY_DIP, HandLandmark.PINKY_TIP]
}


# ==================== LANDMARK SMOOTHING ====================

class LandmarkSmoother:
    """Exponential moving average smoother for landmark coordinates"""
    def __init__(self, alpha=0.5):
        """
        Args:
            alpha: Smoothing factor (0-1). Higher = more responsive, lower = smoother
        """
        self.alpha = alpha
        self.smoothed = None
    
    def update(self, landmark):
        """Update with new landmark and return smoothed value"""
        if self.smoothed is None:
            self.smoothed = landmark
        else:
            self.smoothed = self.alpha * landmark + (1 - self.alpha) * self.smoothed
        return self.smoothed
    
    def reset(self):
        """Reset smoother"""
        self.smoothed = None


# ==================== DATA CLASSES ====================

@dataclass
class Point3D:
    """3D point with x, y, z coordinates"""
    x: float
    y: float
    z: float
    
    def to_array(self) -> np.ndarray:
        return np.array([self.x, self.y, self.z])


@dataclass
class JointAngles:
    """Angles for a single finger"""
    mcp: float = 0.0
    pip: float = 0.0
    dip: float = 0.0
    total: float = 0.0


@dataclass
class FlexionTestResult:
    """Results from flexion/extension test"""
    finger_angles: Dict[str, JointAngles]
    tip_to_mcp_distances: Dict[str, float]
    rom_values: Dict[str, float]
    abnormal_joints: List[str]
    timestamp: float


@dataclass
class AbductionTestResult:
    """Results from abduction/adduction test"""
    inter_finger_angles: Dict[str, float]
    fingertip_distances: Dict[str, float]
    abnormal_pairs: List[str]
    timestamp: float


@dataclass
class OppositionTestResult:
    """Results from thumb opposition test"""
    thumb_to_finger_distances: Dict[str, float]
    successful_oppositions: List[str]
    failed_oppositions: List[str]
    cmc_angle: float
    timestamp: float


@dataclass
class ROMAnalysisReport:
    """Complete ROM analysis report"""
    patient_id: str
    test_date: str
    flexion_result: Optional[FlexionTestResult]
    abduction_result: Optional[AbductionTestResult]
    opposition_result: Optional[OppositionTestResult]
    ra_risk_score: float
    clinical_flags: List[str]


# ==================== GEOMETRY FUNCTIONS ====================

def calculate_angle(p1: Point3D, p2: Point3D, p3: Point3D) -> float:
    """
    Calculate angle at point p2 formed by p1-p2-p3
    Returns angle in degrees
    """
    v1 = p1.to_array() - p2.to_array()
    v2 = p3.to_array() - p2.to_array()
    
    # Normalize vectors
    v1_norm = np.linalg.norm(v1)
    v2_norm = np.linalg.norm(v2)
    
    if v1_norm == 0 or v2_norm == 0:
        return 0.0
    
    v1 = v1 / v1_norm
    v2 = v2 / v2_norm
    
    # Calculate angle using dot product
    dot_product = np.clip(np.dot(v1, v2), -1.0, 1.0)
    angle_rad = np.arccos(dot_product)
    angle_deg = np.degrees(angle_rad)
    
    return angle_deg


def calculate_distance(p1: Point3D, p2: Point3D) -> float:
    """Calculate Euclidean distance between two 3D points"""
    return np.linalg.norm(p1.to_array() - p2.to_array())


def calculate_2d_distance(p1: Point3D, p2: Point3D) -> float:
    """Calculate 2D distance (ignoring z-axis) between two points"""
    return np.sqrt((p1.x - p2.x)**2 + (p1.y - p2.y)**2)


def normalize_distance(distance: float, hand_size: float) -> float:
    """Normalize distance by hand size"""
    if hand_size == 0:
        return 0.0
    return distance / hand_size


# ==================== LANDMARK EXTRACTION ====================

def extract_landmarks(hand_landmarks) -> Dict[int, Point3D]:
    """Extract all hand landmarks as Point3D objects"""
    landmarks = {}
    for idx, landmark in enumerate(hand_landmarks.landmark):
        landmarks[idx] = Point3D(landmark.x, landmark.y, landmark.z)
    return landmarks


def get_hand_size(landmarks: Dict[int, Point3D]) -> float:
    """
    Calculate hand size as distance from wrist to middle finger MCP
    Used for normalization
    """
    wrist = landmarks[HandLandmark.WRIST]
    middle_mcp = landmarks[HandLandmark.MIDDLE_MCP]
    return calculate_distance(wrist, middle_mcp)


# ==================== POSTURE VALIDATION ====================

def validate_hand_posture(landmarks: Dict[int, Point3D], state: TestState) -> Tuple[bool, str]:
    """
    Validate if hand is in correct posture for current test state
    Returns: (is_valid, feedback_message)
    """
    wrist = landmarks[HandLandmark.WRIST]
    middle_mcp = landmarks[HandLandmark.MIDDLE_MCP]
    middle_tip = landmarks[HandLandmark.MIDDLE_TIP]
    index_tip = landmarks[HandLandmark.INDEX_TIP]
    pinky_tip = landmarks[HandLandmark.PINKY_TIP]
    
    # Check if hand is roughly facing camera (palm or back visible)
    # Hand should be relatively flat (z-coordinates similar)
    z_values = [landmarks[i].z for i in [HandLandmark.WRIST, HandLandmark.INDEX_MCP, 
                                          HandLandmark.MIDDLE_MCP, HandLandmark.RING_MCP, 
                                          HandLandmark.PINKY_MCP]]
    z_variance = np.var(z_values)
    
    if z_variance > 0.01:  # Hand is tilted too much
        return False, "⚠️ Keep hand flat facing camera"
    
    # State-specific validation
    if state == TestState.OPEN_HAND_BASELINE:
        # CRITICAL: Fingers must be FULLY extended for accurate baseline
        # Check MCP flexion angles - should be VERY SMALL (<20°) when extended
        mcp_angles = {}
        for finger in ['index', 'middle', 'ring', 'pinky']:
            angles = calculate_finger_angles(landmarks, finger)
            mcp_angles[finger] = angles.mcp
        
        avg_mcp = np.mean(list(mcp_angles.values()))
        max_mcp = max(mcp_angles.values())
        
        # VERY STRICT: MCP must be < 20° (nearly straight!)
        if avg_mcp > 20:
            bent_fingers = [f for f, angle in mcp_angles.items() if angle > 20]
            return False, f"⚠️ STRAIGHTEN {', '.join(bent_fingers)} - MCP: {avg_mcp:.0f}° (need <20°)"
        
        if max_mcp > 30:
            worst_finger = max(mcp_angles, key=mcp_angles.get)
            return False, f"⚠️ STRAIGHTEN {worst_finger} - MCP: {max_mcp:.0f}° (need <30°)"
        
        # Check fingertips are far from wrist
        tip_distances = [
            calculate_distance(landmarks[HandLandmark.INDEX_TIP], wrist),
            calculate_distance(landmarks[HandLandmark.MIDDLE_TIP], wrist),
            calculate_distance(landmarks[HandLandmark.RING_TIP], wrist),
            calculate_distance(landmarks[HandLandmark.PINKY_TIP], wrist)
        ]
        avg_distance = np.mean(tip_distances)
        hand_size = get_hand_size(landmarks)
        
        if avg_distance / hand_size < 1.8:
            return False, "⚠️ Extend fingers MORE - open hand FULLY"
        
        return True, f"✓ Good - Hand fully extended (MCP: {avg_mcp:.0f}°)"
    
    elif state == TestState.FIST_TEST:
        # RELAXED: Check MCP flexion angles - should be >40° for fist
        mcp_angles = {}
        for finger in ['index', 'middle', 'ring', 'pinky']:
            angles = calculate_finger_angles(landmarks, finger)
            mcp_angles[finger] = angles.mcp
        
        avg_mcp = np.mean(list(mcp_angles.values()))
        min_mcp = min(mcp_angles.values())
        
        # RELAXED: MCP must be > 40° (reasonable fist)
        if avg_mcp < 40:
            weak_fingers = [f for f, angle in mcp_angles.items() if angle < 40]
            return False, f"⚠️ Curl {', '.join(weak_fingers)} more - MCP: {avg_mcp:.0f}° (need >40°)"
        
        # Also check fingertips are close to palm
        tip_distances = [
            calculate_distance(landmarks[HandLandmark.INDEX_TIP], landmarks[HandLandmark.INDEX_MCP]),
            calculate_distance(landmarks[HandLandmark.MIDDLE_TIP], landmarks[HandLandmark.MIDDLE_MCP]),
            calculate_distance(landmarks[HandLandmark.RING_TIP], landmarks[HandLandmark.RING_MCP]),
            calculate_distance(landmarks[HandLandmark.PINKY_TIP], landmarks[HandLandmark.PINKY_MCP])
        ]
        avg_distance = np.mean(tip_distances)
        hand_size = get_hand_size(landmarks)
        
        if avg_distance / hand_size > 0.30:  # RELAXED: back to 0.30
            return False, "⚠️ Curl fingers closer to palm"
        
        return True, f"✓ Good - Tight fist (MCP: {avg_mcp:.0f}°)"
    
    elif state == TestState.SPREAD_TEST:
        # Fingers should be spread apart
        # Check distance between adjacent fingertips
        spread_distances = [
            calculate_distance(landmarks[HandLandmark.INDEX_TIP], landmarks[HandLandmark.MIDDLE_TIP]),
            calculate_distance(landmarks[HandLandmark.MIDDLE_TIP], landmarks[HandLandmark.RING_TIP]),
            calculate_distance(landmarks[HandLandmark.RING_TIP], landmarks[HandLandmark.PINKY_TIP])
        ]
        avg_spread = np.mean(spread_distances)
        hand_size = get_hand_size(landmarks)
        
        # STRICTER: Require 50% of hand size between fingers (was 40%)
        if avg_spread / hand_size < 0.50:  # Fingers not spread enough
            return False, "⚠️ Spread fingers MUCH wider apart"
        
        return True, "✓ Good - Fingers spread wide"
    
    elif state in [TestState.THUMB_OPPOSITION_INDEX, TestState.THUMB_OPPOSITION_MIDDLE,
                   TestState.THUMB_OPPOSITION_RING, TestState.THUMB_OPPOSITION_PINKY]:
        # STRICT: Thumb must be TOUCHING specific finger
        thumb_tip = landmarks[HandLandmark.THUMB_TIP]
        
        target_finger = {
            TestState.THUMB_OPPOSITION_INDEX: HandLandmark.INDEX_TIP,
            TestState.THUMB_OPPOSITION_MIDDLE: HandLandmark.MIDDLE_TIP,
            TestState.THUMB_OPPOSITION_RING: HandLandmark.RING_TIP,
            TestState.THUMB_OPPOSITION_PINKY: HandLandmark.PINKY_TIP
        }[state]
        
        finger_tip = landmarks[target_finger]
        distance = calculate_distance(thumb_tip, finger_tip)
        hand_size = get_hand_size(landmarks)
        
        # STRICTER: Require VERY close contact (0.12, not 0.15)
        if distance / hand_size > 0.12:  # Not touching closely enough
            finger_names = {
                TestState.THUMB_OPPOSITION_INDEX: "index",
                TestState.THUMB_OPPOSITION_MIDDLE: "middle",
                TestState.THUMB_OPPOSITION_RING: "ring",
                TestState.THUMB_OPPOSITION_PINKY: "pinky"
            }
            dist_pct = (distance / hand_size) * 100
            return False, f"⚠️ Press thumb to {finger_names[state]} HARDER ({dist_pct:.0f}% gap, need <12%)"
        
        return True, "✓ Good - Fingers touching firmly"
    
    # Default: posture is valid
    return True, "✓ Hand position OK"


# ==================== JOINT ANGLE CALCULATIONS ====================

def calculate_finger_angles(landmarks: Dict[int, Point3D], finger_name: str) -> JointAngles:
    """Calculate MCP, PIP, DIP angles for a finger"""
    finger_lms = FINGER_LANDMARKS[finger_name]
    
    if finger_name == 'thumb':
        # Thumb has different joint structure
        cmc_angle_ext = calculate_angle(
            landmarks[HandLandmark.WRIST],
            landmarks[HandLandmark.THUMB_CMC],
            landmarks[HandLandmark.THUMB_MCP]
        )
        mcp_angle_ext = calculate_angle(
            landmarks[HandLandmark.THUMB_CMC],
            landmarks[HandLandmark.THUMB_MCP],
            landmarks[HandLandmark.THUMB_IP]
        )
        ip_angle_ext = calculate_angle(
            landmarks[HandLandmark.THUMB_MCP],
            landmarks[HandLandmark.THUMB_IP],
            landmarks[HandLandmark.THUMB_TIP]
        )
        
        # Convert to flexion angles (180 - exterior angle)
        cmc_flex = 180 - cmc_angle_ext
        mcp_flex = 180 - mcp_angle_ext
        ip_flex = 180 - ip_angle_ext
        
        return JointAngles(mcp=cmc_flex, pip=mcp_flex, dip=ip_flex, 
                          total=cmc_flex + mcp_flex + ip_flex)
    else:
        # Regular fingers: MCP, PIP, DIP
        wrist = landmarks[HandLandmark.WRIST]
        mcp = landmarks[finger_lms[0]]
        pip = landmarks[finger_lms[1]]
        dip = landmarks[finger_lms[2]]
        tip = landmarks[finger_lms[3]]
        
        # Calculate exterior angles
        mcp_angle_ext = calculate_angle(wrist, mcp, pip)
        pip_angle_ext = calculate_angle(mcp, pip, dip)
        dip_angle_ext = calculate_angle(pip, dip, tip)
        
        # Convert to flexion angles (180 - exterior angle)
        # When finger is straight, exterior angle ≈ 180°, flexion = 0°
        # When finger is bent, exterior angle < 180°, flexion > 0°
        mcp_flex = 180 - mcp_angle_ext
        pip_flex = 180 - pip_angle_ext
        dip_flex = 180 - dip_angle_ext
        
        total = mcp_flex + pip_flex + dip_flex
        
        return JointAngles(mcp=mcp_flex, pip=pip_flex, dip=dip_flex, total=total)



def calculate_tip_to_mcp_distance(landmarks: Dict[int, Point3D], 
                                   finger_name: str, hand_size: float) -> float:
    """Calculate normalized distance from fingertip to MCP (for fist test)"""
    if finger_name == 'thumb':
        return 0.0  # Not applicable for thumb
    
    finger_lms = FINGER_LANDMARKS[finger_name]
    mcp = landmarks[finger_lms[0]]
    tip = landmarks[finger_lms[3]]
    
    distance = calculate_distance(tip, mcp)
    return normalize_distance(distance, hand_size)


# ==================== POSTURE VALIDATION ====================

def validate_posture(landmarks: Dict[int, Point3D], state: TestState, hand_size: float) -> Tuple[bool, str]:
    """
    Validate if hand posture is correct for the current test state
    Returns: (is_valid, correction_message)
    """
    
    if state == TestState.OPEN_HAND_BASELINE or state == TestState.OPEN_HAND_RETURN:
        # Check if fingers are extended (flexion should be minimal)
        issues = []
        for finger_name in ['index', 'middle', 'ring', 'pinky']:
            angles = calculate_finger_angles(landmarks, finger_name)
            # For open hand, each joint should have < 25° flexion (Relaxed from 20)
            if angles.mcp > 25:
                issues.append(f"Straighten {finger_name} finger at knuckle")
            if angles.pip > 25:
                issues.append(f"Straighten {finger_name} finger at middle joint")
            if angles.dip > 25:
                issues.append(f"Straighten {finger_name} finger at tip")
        
        # Check if fingers are together (not spread)
        wrist = landmarks[HandLandmark.WRIST]
        index_tip = landmarks[HandLandmark.INDEX_TIP]
        middle_tip = landmarks[HandLandmark.MIDDLE_TIP]
        angle_between = calculate_angle(index_tip, wrist, middle_tip)
        
        if angle_between > 15:
            issues.append("Keep fingers together, don't spread them")
        
        if issues:
            return False, "⚠️ OPEN HAND: " + " | ".join(issues[:2])  # Show max 2 issues
        return True, "✓ Open hand posture correct"
    
    elif state == TestState.FIST_TEST:
        # Check if fingers are flexed (making a fist)
        issues = []
        for finger_name in ['index', 'middle', 'ring', 'pinky']:
            angles = calculate_finger_angles(landmarks, finger_name)
            # For fist, total flexion should be > 140° (Relaxed from 150)
            if angles.total < 140:
                issues.append(f"Bend {finger_name} finger more tightly")
            
            # Check if fingertip is close to palm
            finger_lms = FINGER_LANDMARKS[finger_name]
            tip = landmarks[finger_lms[3]]
            mcp = landmarks[finger_lms[0]]
            distance = calculate_distance(tip, mcp)
            norm_dist = normalize_distance(distance, hand_size)
            
            # Fingertip should be close to MCP (tight fist)
            # Use NORMAL_THRESHOLDS['tip_to_mcp_max'] which is now 0.30
            if norm_dist > NORMAL_THRESHOLDS['tip_to_mcp_max']: 
                issues.append(f"Curl {finger_name} finger closer to palm")
        
        if issues:
            return False, "⚠️ FIST: " + " | ".join(issues[:2])
        return True, "✓ Fist posture correct"
    
    elif state == TestState.SPREAD_TEST:
        # Check if fingers are spread apart
        issues = []
        wrist = landmarks[HandLandmark.WRIST]
        
        # Check angles between adjacent fingers
        finger_pairs = [
            ('index', 'middle', HandLandmark.INDEX_TIP, HandLandmark.MIDDLE_TIP, NORMAL_THRESHOLDS['finger_abduction']),
            ('middle', 'ring', HandLandmark.MIDDLE_TIP, HandLandmark.RING_TIP, NORMAL_THRESHOLDS['finger_abduction']),
            ('ring', 'pinky', HandLandmark.RING_TIP, HandLandmark.PINKY_TIP, NORMAL_THRESHOLDS['finger_abduction'])
        ]
        
        for f1, f2, tip1_idx, tip2_idx, min_angle in finger_pairs:
            tip1 = landmarks[tip1_idx]
            tip2 = landmarks[tip2_idx]
            angle = calculate_angle(tip1, wrist, tip2)
            
            if angle < min_angle:
                issues.append(f"Spread {f1} and {f2} fingers wider apart")
        
        # Check if fingers are straight
        for finger_name in ['index', 'middle', 'ring', 'pinky']:
            angles = calculate_finger_angles(landmarks, finger_name)
            if angles.total > 40:  # Should be mostly straight (Relaxed from 30)
                issues.append(f"Straighten {finger_name} finger while spreading")
        
        if issues:
            return False, "⚠️ SPREAD: " + " | ".join(issues[:2])
        return True, "✓ Spread posture correct"
    
    elif state in [TestState.THUMB_OPPOSITION_INDEX, TestState.THUMB_OPPOSITION_MIDDLE,
                   TestState.THUMB_OPPOSITION_RING, TestState.THUMB_OPPOSITION_PINKY]:
        # Check thumb opposition
        thumb_tip = landmarks[HandLandmark.THUMB_TIP]
        
        # Determine target finger
        target_fingers = {
            TestState.THUMB_OPPOSITION_INDEX: ('index', HandLandmark.INDEX_TIP),
            TestState.THUMB_OPPOSITION_MIDDLE: ('middle', HandLandmark.MIDDLE_TIP),
            TestState.THUMB_OPPOSITION_RING: ('ring', HandLandmark.RING_TIP),
            TestState.THUMB_OPPOSITION_PINKY: ('pinky', HandLandmark.PINKY_TIP)
        }
        
        finger_name, finger_tip_idx = target_fingers[state]
        finger_tip = landmarks[finger_tip_idx]
        
        # Calculate distance between thumb and target finger
        distance = calculate_distance(thumb_tip, finger_tip)
        norm_dist = normalize_distance(distance, hand_size)
        
        # Should be very close (touching)
        # Use NORMAL_THRESHOLDS['opposition_distance'] which is now 0.12
        if norm_dist > NORMAL_THRESHOLDS['opposition_distance']:
            return False, f"⚠️ OPPOSITION: Bring thumb and {finger_name} finger closer together (touching)"
        
        # Check if other fingers are relatively straight/neutral
        other_fingers = [f for f in ['index', 'middle', 'ring', 'pinky'] if f != finger_name]
        for other_finger in other_fingers:
            angles = calculate_finger_angles(landmarks, other_finger)
            if angles.total > 70:  # Too bent (Relaxed from 60)
                return False, f"⚠️ OPPOSITION: Keep {other_finger} finger more relaxed/straight"
        
        return True, f"✓ Thumb-{finger_name} opposition correct"
    
    # Default: no validation needed
    return True, ""


# ==================== TEST 1: FLEXION/EXTENSION ====================

def perform_flexion_test(landmarks: Dict[int, Point3D], hand_size: float) -> FlexionTestResult:
    """
    Perform finger flexion/extension test (Fist Test)
    Measures ROM at MCP, PIP, DIP joints
    """
    finger_angles = {}
    tip_to_mcp_distances = {}
    rom_values = {}
    abnormal_joints = []
    
    for finger_name in ['index', 'middle', 'ring', 'pinky']:
        # Calculate joint angles
        angles = calculate_finger_angles(landmarks, finger_name)
        finger_angles[finger_name] = angles
        
        # Calculate tip-to-MCP distance
        tip_dist = calculate_tip_to_mcp_distance(landmarks, finger_name, hand_size)
        tip_to_mcp_distances[finger_name] = tip_dist
        
        # Calculate ROM (total flexion achieved)
        rom_values[finger_name] = angles.total
        
        # Check for abnormalities
        if angles.mcp < NORMAL_THRESHOLDS['mcp_flexion'] * 0.8:
            abnormal_joints.append(f"{finger_name}_mcp")
        if angles.pip < NORMAL_THRESHOLDS['pip_flexion'] * 0.8:
            abnormal_joints.append(f"{finger_name}_pip")
        if angles.dip < NORMAL_THRESHOLDS['dip_flexion'] * 0.8:
            abnormal_joints.append(f"{finger_name}_dip")
    
    return FlexionTestResult(
        finger_angles=finger_angles,
        tip_to_mcp_distances=tip_to_mcp_distances,
        rom_values=rom_values,
        abnormal_joints=abnormal_joints,
        timestamp=time.time()
    )


# ==================== TEST 2: ABDUCTION/ADDUCTION ====================

def perform_abduction_test(landmarks: Dict[int, Point3D], hand_size: float) -> AbductionTestResult:
    """
    Perform finger abduction/adduction test (Spread Test)
    Measures inter-finger angles and distances
    """
    inter_finger_angles = {}
    fingertip_distances = {}
    abnormal_pairs = []
    
    # Define finger pairs for measurement
    finger_pairs = [
        ('thumb', 'index', HandLandmark.THUMB_TIP, HandLandmark.INDEX_TIP),
        ('index', 'middle', HandLandmark.INDEX_TIP, HandLandmark.MIDDLE_TIP),
        ('middle', 'ring', HandLandmark.MIDDLE_TIP, HandLandmark.RING_TIP),
        ('ring', 'pinky', HandLandmark.RING_TIP, HandLandmark.PINKY_TIP)
    ]
    
    wrist = landmarks[HandLandmark.WRIST]
    
    for f1_name, f2_name, f1_tip_idx, f2_tip_idx in finger_pairs:
        # Calculate angle between fingers (using wrist as vertex)
        f1_tip = landmarks[f1_tip_idx]
        f2_tip = landmarks[f2_tip_idx]
        
        angle = calculate_angle(f1_tip, wrist, f2_tip)
        pair_name = f"{f1_name}_{f2_name}"
        inter_finger_angles[pair_name] = angle
        
        # Calculate fingertip distance
        distance = calculate_distance(f1_tip, f2_tip)
        normalized_dist = normalize_distance(distance, hand_size)
        fingertip_distances[pair_name] = normalized_dist
        
        # Check for abnormalities (reduced spreading)
        if angle < NORMAL_THRESHOLDS['finger_abduction'] * 0.8:
            abnormal_pairs.append(pair_name)
    
    return AbductionTestResult(
        inter_finger_angles=inter_finger_angles,
        fingertip_distances=fingertip_distances,
        abnormal_pairs=abnormal_pairs,
        timestamp=time.time()
    )


# ==================== TEST 3: THUMB OPPOSITION ====================

def perform_opposition_test(landmarks: Dict[int, Point3D], hand_size: float) -> OppositionTestResult:
    """
    Perform thumb opposition test
    Measures thumb-to-fingertip distances
    """
    thumb_tip = landmarks[HandLandmark.THUMB_TIP]
    thumb_to_finger_distances = {}
    successful_oppositions = []
    failed_oppositions = []
    
    # Test opposition to each finger
    opposition_targets = {
        'index': HandLandmark.INDEX_TIP,
        'middle': HandLandmark.MIDDLE_TIP,
        'ring': HandLandmark.RING_TIP,
        'pinky': HandLandmark.PINKY_TIP
    }
    
    for finger_name, tip_idx in opposition_targets.items():
        finger_tip = landmarks[tip_idx]
        distance = calculate_distance(thumb_tip, finger_tip)
        normalized_dist = normalize_distance(distance, hand_size)
        thumb_to_finger_distances[finger_name] = normalized_dist
        
        # Check if opposition is successful
        if normalized_dist < NORMAL_THRESHOLDS['opposition_distance']:
            successful_oppositions.append(finger_name)
        else:
            failed_oppositions.append(finger_name)
    
    # Calculate CMC angle
    cmc_angle = calculate_angle(
        landmarks[HandLandmark.WRIST],
        landmarks[HandLandmark.THUMB_CMC],
        landmarks[HandLandmark.THUMB_MCP]
    )
    
    return OppositionTestResult(
        thumb_to_finger_distances=thumb_to_finger_distances,
        successful_oppositions=successful_oppositions,
        failed_oppositions=failed_oppositions,
        cmc_angle=cmc_angle,
        timestamp=time.time()
    )


# ==================== ROM CALCULATION (MEDICAL STANDARD) ====================

def calculate_joint_rom(baseline_angles: Dict[str, JointAngles], 
                       flexion_angles: Dict[str, JointAngles]) -> Dict[str, Dict[str, float]]:
    """
    Calculate Range of Motion (ROM) as difference from baseline
    
    Medical Standard:
    ROM = |Baseline Angle - Flexion Angle|
    
    Args:
        baseline_angles: Joint angles in open hand position
        flexion_angles: Joint angles in fist position
    
    Returns:
        Dictionary of ROM values for each finger and joint
    """
    rom = {}
    
    for finger in ['index', 'middle', 'ring', 'pinky']:
        if finger in baseline_angles and finger in flexion_angles:
            baseline = baseline_angles[finger]
            flexion = flexion_angles[finger]
            
            rom[finger] = {
                'mcp': abs(baseline.mcp - flexion.mcp),
                'pip': abs(baseline.pip - flexion.pip),
                'dip': abs(baseline.dip - flexion.dip),
                'total': abs(baseline.total - flexion.total)
            }
    
    return rom


def perform_flexion_test(landmarks: Dict[int, Point3D], hand_size: float,
                        baseline_angles: Optional[Dict[str, JointAngles]] = None) -> FlexionTestResult:
    """
    Perform flexion test and calculate ROM if baseline is provided
    
    Args:
        landmarks: Current hand landmarks
        hand_size: Hand size for normalization
        baseline_angles: Baseline angles from open hand (if available)
    
    Returns:
        FlexionTestResult with angles and ROM values
    """
    finger_angles = {}
    tip_to_mcp_distances = {}
    abnormal_joints = []
    
    # Calculate current angles
    for finger_name in ['index', 'middle', 'ring', 'pinky']:
        angles = calculate_finger_angles(landmarks, finger_name)
        finger_angles[finger_name] = angles
        
        # Calculate tip-to-MCP distance
        finger_lms = FINGER_LANDMARKS[finger_name]
        mcp = landmarks[finger_lms[0]]
        tip = landmarks[finger_lms[3]]
        distance = calculate_distance(tip, mcp)
        normalized_dist = normalize_distance(distance, hand_size)
        tip_to_mcp_distances[finger_name] = normalized_dist
    
    # Calculate ROM if baseline is provided
    rom_values = {}
    if baseline_angles:
        rom_values = calculate_joint_rom(baseline_angles, finger_angles)
        
        # Check for abnormal ROM (< 80% of normal)
        for finger, rom in rom_values.items():
            # MCP normal: 85-90°, threshold: 68°
            if rom['mcp'] < NORMAL_THRESHOLDS['mcp_flexion'] * 0.8:
                abnormal_joints.append(f"{finger}_mcp")
            
            # PIP normal: 100-110°, threshold: 80°
            if rom['pip'] < NORMAL_THRESHOLDS['pip_flexion'] * 0.8:
                abnormal_joints.append(f"{finger}_pip")
            
            # DIP normal: 80-90°, threshold: 64°
            if rom['dip'] < NORMAL_THRESHOLDS['dip_flexion'] * 0.8:
                abnormal_joints.append(f"{finger}_dip")
    
    return FlexionTestResult(
        finger_angles=finger_angles,
        tip_to_mcp_distances=tip_to_mcp_distances,
        rom_values=rom_values,
        abnormal_joints=abnormal_joints,
        timestamp=time.time()
    )


def perform_abduction_test(landmarks: Dict[int, Point3D], hand_size: float) -> AbductionTestResult:
    """Perform finger abduction/spreading test"""
    inter_finger_angles = {}
    fingertip_distances = {}
    abnormal_pairs = []
    
    # Calculate angles between adjacent fingers
    finger_pairs = [
        ('thumb', 'index', HandLandmark.THUMB_TIP, HandLandmark.INDEX_TIP),
        ('index', 'middle', HandLandmark.INDEX_TIP, HandLandmark.MIDDLE_TIP),
        ('middle', 'ring', HandLandmark.MIDDLE_TIP, HandLandmark.RING_TIP),
        ('ring', 'pinky', HandLandmark.RING_TIP, HandLandmark.PINKY_TIP)
    ]
    
    wrist = landmarks[HandLandmark.WRIST]
    
    for f1_name, f2_name, f1_tip_idx, f2_tip_idx in finger_pairs:
        f1_tip = landmarks[f1_tip_idx]
        f2_tip = landmarks[f2_tip_idx]
        
        # Calculate angle
        angle = calculate_angle(f1_tip, wrist, f2_tip)
        pair_name = f"{f1_name}_{f2_name}"
        inter_finger_angles[pair_name] = angle
        
        # Calculate distance
        distance = calculate_distance(f1_tip, f2_tip)
        normalized_dist = normalize_distance(distance, hand_size)
        fingertip_distances[pair_name] = normalized_dist
        
        # Check if abnormal (< 80% of normal)
        if 'thumb' not in pair_name and angle < NORMAL_THRESHOLDS['finger_abduction'] * 0.8:
            abnormal_pairs.append(pair_name)
    
    return AbductionTestResult(
        inter_finger_angles=inter_finger_angles,
        fingertip_distances=fingertip_distances,
        abnormal_pairs=abnormal_pairs,
        timestamp=time.time()
    )


def perform_opposition_test(landmarks: Dict[int, Point3D], hand_size: float) -> OppositionTestResult:
    """Perform thumb opposition test"""
    thumb_tip = landmarks[HandLandmark.THUMB_TIP]
    thumb_to_finger_distances = {}
    successful_oppositions = []
    failed_oppositions = []
    
    # Test opposition to each finger
    opposition_targets = {
        'index': HandLandmark.INDEX_TIP,
        'middle': HandLandmark.MIDDLE_TIP,
        'ring': HandLandmark.RING_TIP,
        'pinky': HandLandmark.PINKY_TIP
    }
    
    for finger_name, tip_idx in opposition_targets.items():
        finger_tip = landmarks[tip_idx]
        distance = calculate_distance(thumb_tip, finger_tip)
        normalized_dist = normalize_distance(distance, hand_size)
        thumb_to_finger_distances[finger_name] = normalized_dist
        
        # Check if opposition is successful
        if normalized_dist < NORMAL_THRESHOLDS['opposition_distance']:
            successful_oppositions.append(finger_name)
        else:
            failed_oppositions.append(finger_name)
    
    # Calculate CMC angle
    cmc_angle = calculate_angle(
        landmarks[HandLandmark.WRIST],
        landmarks[HandLandmark.THUMB_CMC],
        landmarks[HandLandmark.THUMB_MCP]
    )
    
    return OppositionTestResult(
        thumb_to_finger_distances=thumb_to_finger_distances,
        successful_oppositions=successful_oppositions,
        failed_oppositions=failed_oppositions,
        cmc_angle=cmc_angle,
        timestamp=time.time()
    )


# ==================== RA RISK ASSESSMENT ====================

def calculate_ra_risk_score(flexion_result: Optional[FlexionTestResult],
                            abduction_result: Optional[AbductionTestResult],
                            opposition_result: Optional[OppositionTestResult]) -> Tuple[float, List[str]]:
    """
    Calculate RA risk score (0-100) based on test results
    Returns (risk_score, clinical_flags)
    """
    risk_score = 0.0
    clinical_flags = []
    
    # Flexion test contribution (40 points)
    if flexion_result:
        abnormal_count = len(flexion_result.abnormal_joints)
        if abnormal_count > 0:
            risk_score += min(40, abnormal_count * 5)
            clinical_flags.append(f"Reduced flexion in {abnormal_count} joints")
        
        # Check tip-to-MCP distances (inability to make fist)
        poor_fist_count = sum(1 for dist in flexion_result.tip_to_mcp_distances.values() 
                              if dist > NORMAL_THRESHOLDS['tip_to_mcp_max'])
        if poor_fist_count > 0:
            risk_score += min(15, poor_fist_count * 5)
            clinical_flags.append(f"Incomplete fist closure in {poor_fist_count} fingers")
    
    # Abduction test contribution (30 points)
    if abduction_result:
        abnormal_count = len(abduction_result.abnormal_pairs)
        if abnormal_count > 0:
            risk_score += min(30, abnormal_count * 10)
            clinical_flags.append(f"Reduced finger spreading in {abnormal_count} finger pairs")
    
    # Opposition test contribution (30 points)
    if opposition_result:
        failed_count = len(opposition_result.failed_oppositions)
        if failed_count > 0:
            risk_score += min(30, failed_count * 10)
            clinical_flags.append(f"Failed thumb opposition to {failed_count} fingers")
    
    # Cap at 100
    risk_score = min(100, risk_score)
    
    # Add overall assessment flag
    if risk_score >= 70:
        clinical_flags.insert(0, "HIGH RISK: Multiple ROM limitations detected")
    elif risk_score >= 40:
        clinical_flags.insert(0, "MODERATE RISK: Some ROM limitations detected")
    elif risk_score >= 20:
        clinical_flags.insert(0, "LOW RISK: Minor ROM limitations detected")
    else:
        clinical_flags.insert(0, "NORMAL: No significant ROM limitations")
    
    return risk_score, clinical_flags


# ==================== VISUALIZATION ====================

def draw_hand_landmarks(image: np.ndarray, hand_landmarks, mp_hands, mp_drawing) -> np.ndarray:
    """Draw hand landmarks on image"""
    mp_drawing.draw_landmarks(
        image,
        hand_landmarks,
        mp_hands.HAND_CONNECTIONS,
        mp_drawing.DrawingSpec(color=(0, 255, 0), thickness=2, circle_radius=2),
        mp_drawing.DrawingSpec(color=(255, 0, 0), thickness=2)
    )
    return image


def draw_test_state(image: np.ndarray, state: TestState, instruction: str) -> np.ndarray:
    """Draw current test state and instructions on image"""
    h, w = image.shape[:2]
    
    # Draw semi-transparent overlay at top
    overlay = image.copy()
    cv2.rectangle(overlay, (0, 0), (w, 120), (0, 0, 0), -1)
    image = cv2.addWeighted(overlay, 0.6, image, 0.4, 0)
    
    # Draw state
    state_text = f"State: {state.name.replace('_', ' ')}"
    cv2.putText(image, state_text, (10, 30), cv2.FONT_HERSHEY_SIMPLEX, 
                0.8, (0, 255, 255), 2)
    
    # Draw instruction
    cv2.putText(image, instruction, (10, 70), cv2.FONT_HERSHEY_SIMPLEX, 
                0.7, (255, 255, 255), 2)
    
    # Draw progress indicator
    progress = (state.value / len(TestState)) * 100
    cv2.rectangle(image, (10, 90), (w - 10, 110), (50, 50, 50), -1)
    cv2.rectangle(image, (10, 90), (int(10 + (w - 20) * progress / 100), 110), 
                  (0, 255, 0), -1)
    
    return image


def draw_joint_angles(image: np.ndarray, landmarks: Dict[int, Point3D], 
                      finger_name: str, angles: JointAngles, h: int, w: int) -> np.ndarray:
    """Draw joint angles on image"""
    # Convert normalized coordinates to pixel coordinates
    finger_lms = FINGER_LANDMARKS[finger_name]
    mcp = landmarks[finger_lms[0]]
    
    # Draw angle text near MCP
    x = int(mcp.x * w)
    y = int(mcp.y * h)
    
    text = f"{finger_name[:3].upper()}: {angles.total:.0f}°"
    cv2.putText(image, text, (x + 10, y), cv2.FONT_HERSHEY_SIMPLEX, 
                0.4, (255, 255, 0), 1)
    
    return image


def draw_results_summary(image: np.ndarray) -> np.ndarray:
    """Draw simple completion message"""
    h, w = image.shape[:2]
    
    # Draw semi-transparent overlay at bottom
    overlay = image.copy()
    cv2.rectangle(overlay, (0, h - 100), (w, h), (0, 0, 0), -1)
    image = cv2.addWeighted(overlay, 0.7, image, 0.3, 0)
    
    # Draw completion text
    cv2.putText(image, "Analysis Complete", (10, h - 40), cv2.FONT_HERSHEY_SIMPLEX, 
                1.0, (0, 255, 0), 2)
    
    return image


# ==================== MAIN APPLICATION ====================

class HandROMAnalyzer:
    """Main application class for hand ROM analysis"""
    
    def __init__(self, video_source=0):
        """
        Initialize the analyzer
        Args:
            video_source: 0 for webcam, or path to video file
        """
        self.video_source = video_source
        self.mp_hands = mp.solutions.hands
        self.mp_drawing = mp.solutions.drawing_utils
        self.hands = self.mp_hands.Hands(
            static_image_mode=False,
            max_num_hands=1,
            min_detection_confidence=0.8,  # Increased for better quality
            min_tracking_confidence=0.8     # Increased for better quality
        )
        
        # Test state management
        self.current_state = TestState.WAITING
        self.state_start_time = time.time()
        # Removed auto-timer - now requires manual confirmation
        
        # Detection quality tracking
        self.detection_quality = 0.0
        self.angle_stability_buffer = []  # Track angle stability
        self.stability_window = 10  # frames to check for stability
        
        # Posture validation
        self.posture_valid = True
        self.posture_message = ""
        
        # Results storage
        self.flexion_result = None
        self.abduction_result = None
        self.opposition_result = None
        self.baseline_open_hand = None
        self.baseline_angles = None  # Store baseline angles for ROM calculation
        
        # Temporary storage for opposition test
        self.opposition_results = {}
        
        # Landmark smoothers for noise reduction
        self.landmark_smoothers = {}
        
        # Image capture setup
        self.session_id = time.strftime("%Y%m%d_%H%M%S")
        self.output_dir = f"output_sessions/rom_analysis_{self.session_id}"
        os.makedirs(self.output_dir, exist_ok=True)
        self.captured_images = {}
        print(f"\n📁 Images will be saved to: {self.output_dir}/")
        
    def get_state_instruction(self) -> str:
        """Get instruction text for current state"""
        instructions = {
            TestState.WAITING: "Press SPACE to start the test",
            TestState.OPEN_HAND_BASELINE: "Hold your hand OPEN with fingers extended - Press SPACE when ready",
            TestState.FIST_TEST: "Make a FIST - close your fingers tightly - Press SPACE when ready",
            TestState.OPEN_HAND_RETURN: "Open your hand again - Press SPACE when ready",
            TestState.SPREAD_TEST: "SPREAD your fingers apart as wide as possible - Press SPACE when ready",
            TestState.CLOSE_HAND_RETURN: "Close fingers back to neutral - Press SPACE when ready",
            TestState.THUMB_OPPOSITION_INDEX: "Touch your THUMB to INDEX finger - Press SPACE when ready",
            TestState.THUMB_OPPOSITION_MIDDLE: "Touch your THUMB to MIDDLE finger - Press SPACE when ready",
            TestState.THUMB_OPPOSITION_RING: "Touch your THUMB to RING finger - Press SPACE when ready",
            TestState.THUMB_OPPOSITION_PINKY: "Touch your THUMB to PINKY finger - Press SPACE when ready",
            TestState.COMPLETE: "Test complete! Press 'S' to save, 'R' to restart, 'Q' to quit"
        }
        return instructions.get(self.current_state, "")
    
    def advance_state(self):
        """Advance to next test state"""
        current_value = self.current_state.value
        if current_value < len(TestState) - 1:
            self.current_state = TestState(current_value + 1)
            self.state_start_time = time.time()
            # Reset stability tracking for new state
            self.angle_stability_buffer = []
            self.detection_quality = 0.0
    
    def save_frame_image(self, frame: np.ndarray, state: TestState, landmarks: Dict[int, Point3D]):
        """Save annotated frame image with joint angles"""
        # Create a copy for annotation
        annotated_frame = frame.copy()
        h, w = annotated_frame.shape[:2]
        
        # Draw joint angles for each finger
        if state in [TestState.FIST_TEST, TestState.OPEN_HAND_BASELINE, TestState.SPREAD_TEST]:
            for finger_name in ['index', 'middle', 'ring', 'pinky']:
                angles = calculate_finger_angles(landmarks, finger_name)
                annotated_frame = draw_joint_angles(annotated_frame, landmarks, finger_name, angles, h, w)
        
        # Draw opposition measurements
        elif state in [TestState.THUMB_OPPOSITION_INDEX, TestState.THUMB_OPPOSITION_MIDDLE,
                      TestState.THUMB_OPPOSITION_RING, TestState.THUMB_OPPOSITION_PINKY]:
            # Draw CMC angle
            cmc_angle = calculate_angle(
                landmarks[HandLandmark.WRIST],
                landmarks[HandLandmark.THUMB_CMC],
                landmarks[HandLandmark.THUMB_MCP]
            )
            # Draw CMC angle text near thumb base
            cmc_pt = landmarks[HandLandmark.THUMB_CMC]
            cv2.putText(annotated_frame, f"CMC: {cmc_angle:.1f}", 
                      (int(cmc_pt.x * w) + 20, int(cmc_pt.y * h)), 
                      cv2.FONT_HERSHEY_SIMPLEX, 0.5, (0, 255, 255), 1)
            
            # Draw distance line
            target_fingers = {
                TestState.THUMB_OPPOSITION_INDEX: HandLandmark.INDEX_TIP,
                TestState.THUMB_OPPOSITION_MIDDLE: HandLandmark.MIDDLE_TIP,
                TestState.THUMB_OPPOSITION_RING: HandLandmark.RING_TIP,
                TestState.THUMB_OPPOSITION_PINKY: HandLandmark.PINKY_TIP
            }
            target_idx = target_fingers[state]
            thumb_tip = landmarks[HandLandmark.THUMB_TIP]
            target_tip = landmarks[target_idx]
            
            # Draw line between tips
            pt1 = (int(thumb_tip.x * w), int(thumb_tip.y * h))
            pt2 = (int(target_tip.x * w), int(target_tip.y * h))
            cv2.line(annotated_frame, pt1, pt2, (0, 0, 255), 2)
            
            # Calculate and draw distance text
            dist = calculate_distance(thumb_tip, target_tip)
            mid_x = (pt1[0] + pt2[0]) // 2
            mid_y = (pt1[1] + pt2[1]) // 2
            cv2.putText(annotated_frame, f"Dist: {dist:.3f}", (mid_x, mid_y - 10),
                      cv2.FONT_HERSHEY_SIMPLEX, 0.5, (0, 0, 255), 1)
        
        # Save image
        state_name = state.name.lower()
        filename = f"{self.output_dir}/{state_name}.jpg"
        cv2.imwrite(filename, annotated_frame)
        self.captured_images[state_name] = filename
        print(f"📸 Captured: {filename}")
    
    def calculate_detection_quality(self, landmarks: Dict[int, Point3D], hand_size: float) -> float:
        """Calculate quality score for current hand detection (0-100)"""
        quality_score = 100.0
        
        # Check 1: Hand size should be reasonable (not too small/far)
        if hand_size < 0.1:  # Too small
            quality_score -= 30
        elif hand_size > 0.5:  # Too close
            quality_score -= 20
        
        # Check 2: All landmarks should be within frame bounds
        out_of_bounds = sum(1 for lm in landmarks.values() 
                           if lm.x < 0 or lm.x > 1 or lm.y < 0 or lm.y > 1)
        quality_score -= out_of_bounds * 5
        
        # Check 3: Finger joints should form reasonable angles
        for finger_name in ['index', 'middle', 'ring', 'pinky']:
            angles = calculate_finger_angles(landmarks, finger_name)
            # Check if angles are physically possible (0-180 degrees)
            if angles.mcp < 0 or angles.mcp > 180:
                quality_score -= 10
            if angles.pip < 0 or angles.pip > 180:
                quality_score -= 10
            if angles.dip < 0 or angles.dip > 180:
                quality_score -= 10
        
        return max(0, min(100, quality_score))
    
    def check_angle_stability(self, landmarks: Dict[int, Point3D]) -> bool:
        """Check if angles are stable (not moving too much)"""
        # Calculate current total angles for all fingers
        current_angles = []
        for finger_name in ['index', 'middle', 'ring', 'pinky']:
            angles = calculate_finger_angles(landmarks, finger_name)
            current_angles.append(angles.total)
        
        # Add to buffer
        self.angle_stability_buffer.append(current_angles)
        
        # Keep only recent frames
        if len(self.angle_stability_buffer) > self.stability_window:
            self.angle_stability_buffer.pop(0)
        
        # Need enough frames to check stability
        if len(self.angle_stability_buffer) < self.stability_window:
            return False
        
        # Check variance - if angles are changing too much, not stable
        for finger_idx in range(4):
            finger_angles = [frame[finger_idx] for frame in self.angle_stability_buffer]
            variance = np.var(finger_angles)
            # If variance is high (>25 degrees squared), not stable
            if variance > 25:
                return False
        
        return True
    
    def process_frame(self, landmarks: Dict[int, Point3D], hand_size: float, current_frame: np.ndarray = None):
        """Process current frame based on state - NO AUTO-ADVANCE"""
        # Update detection quality
        self.detection_quality = self.calculate_detection_quality(landmarks, hand_size)
        
        # Check angle stability
        is_stable = self.check_angle_stability(landmarks)
        
        # Validate posture for current state
        self.posture_valid, self.posture_message = validate_hand_posture(landmarks, self.current_state)
        
        # Auto-capture when all conditions are met
        if self.detection_quality >= 70 and is_stable and self.posture_valid:
            # Check if we've been ready long enough (2 seconds countdown)
            if not hasattr(self, 'ready_start_time'):
                self.ready_start_time = time.time()
            
            elapsed = time.time() - self.ready_start_time
            if elapsed >= 2.0:  # 2 second countdown
                # AUTO-CAPTURE!
                if current_frame is not None:
                    self.save_frame_image(current_frame, self.current_state, landmarks)
                
                # Capture measurements
                if self.current_state == TestState.OPEN_HAND_BASELINE:
                    self.baseline_open_hand = landmarks.copy()
                    # Store baseline angles for ROM calculation
                    self.baseline_angles = {
                        'index': calculate_finger_angles(landmarks, 'index'),
                        'middle': calculate_finger_angles(landmarks, 'middle'),
                        'ring': calculate_finger_angles(landmarks, 'ring'),
                        'pinky': calculate_finger_angles(landmarks, 'pinky')
                    }
                    print(f"✓ Baseline captured - Ready for ROM measurement")
                    
                elif self.current_state == TestState.FIST_TEST:
                    # Pass baseline angles for ROM calculation
                    self.flexion_result = perform_flexion_test(landmarks, hand_size, self.baseline_angles)
                    
                elif self.current_state == TestState.SPREAD_TEST:
                    self.abduction_result = perform_abduction_test(landmarks, hand_size)
                    
                elif self.current_state == TestState.THUMB_OPPOSITION_INDEX:
                    result = perform_opposition_test(landmarks, hand_size)
                    self.opposition_results['index'] = result.thumb_to_finger_distances['index']
                    
                elif self.current_state == TestState.THUMB_OPPOSITION_MIDDLE:
                    result = perform_opposition_test(landmarks, hand_size)
                    self.opposition_results['middle'] = result.thumb_to_finger_distances['middle']
                    
                elif self.current_state == TestState.THUMB_OPPOSITION_RING:
                    result = perform_opposition_test(landmarks, hand_size)
                    self.opposition_results['ring'] = result.thumb_to_finger_distances['ring']
                    
                elif self.current_state == TestState.THUMB_OPPOSITION_PINKY:
                    result = perform_opposition_test(landmarks, hand_size)
                    self.opposition_results['pinky'] = result.thumb_to_finger_distances['pinky']
                    self.opposition_result = perform_opposition_test(landmarks, hand_size)
                
                # Advance to next state
                self.advance_state()
                # Reset ready timer
                if hasattr(self, 'ready_start_time'):
                    delattr(self, 'ready_start_time')
        else:
            # Not ready - reset timer
            if hasattr(self, 'ready_start_time'):
                delattr(self, 'ready_start_time')


    
    def generate_report(self) -> ROMAnalysisReport:
        """Generate final analysis report"""
        risk_score, clinical_flags = calculate_ra_risk_score(
            self.flexion_result,
            self.abduction_result,
            self.opposition_result
        )
        
        report = ROMAnalysisReport(
            patient_id="PATIENT_001",
            test_date=time.strftime("%Y-%m-%d %H:%M:%S"),
            flexion_result=self.flexion_result,
            abduction_result=self.abduction_result,
            opposition_result=self.opposition_result,
            ra_risk_score=risk_score,
            clinical_flags=clinical_flags
        )
        
        return report
    
    def save_report(self, report: ROMAnalysisReport, filename: str = None):
        """Save report to JSON and TXT files"""
        if filename is None:
            filename = f"{self.output_dir}/rom_analysis_report.json"
        
        # Convert dataclasses to dict
        report_dict = {
            'patient_id': report.patient_id,
            'test_date': report.test_date,
            'ra_risk_score': report.ra_risk_score,
            'clinical_flags': report.clinical_flags,
            'flexion_result': asdict(report.flexion_result) if report.flexion_result else None,
            'abduction_result': asdict(report.abduction_result) if report.abduction_result else None,
            'opposition_result': asdict(report.opposition_result) if report.opposition_result else None,
            'captured_images': self.captured_images
        }
        
        # Save JSON report
        with open(filename, 'w') as f:
            json.dump(report_dict, f, indent=2)
        
        # Save TXT summary
        summary_filename = f"{self.output_dir}/rom_analysis_summary.txt"
        with open(summary_filename, 'w', encoding='utf-8') as f:
            f.write("="*60 + "\n")
            f.write("HAND ROM ANALYSIS REPORT\n")
            f.write("="*60 + "\n\n")
            f.write(f"Patient ID: {report.patient_id}\n")
            f.write(f"Test Date: {report.test_date}\n\n")
            
            # Flexion Test Results
            if report.flexion_result:
                f.write("FLEXION/EXTENSION TEST:\n")
                for finger, angles in report.flexion_result.finger_angles.items():
                    f.write(f"  {finger.capitalize()}:\n")
                    f.write(f"    MCP: {angles.mcp:.1f} degrees\n")
                    f.write(f"    PIP: {angles.pip:.1f} degrees\n")
                    f.write(f"    DIP: {angles.dip:.1f} degrees\n")
                    f.write(f"    Total ROM: {angles.total:.1f} degrees\n")
                if report.flexion_result.abnormal_joints:
                    f.write(f"  Abnormal joints: {', '.join(report.flexion_result.abnormal_joints)}\n")
                else:
                    f.write(f"  Status: All joints within normal range\n")
                f.write("\n")
            
            # Abduction Test Results
            if report.abduction_result:
                f.write("ABDUCTION/ADDUCTION TEST (Finger Spreading):\n")
                for pair, angle in report.abduction_result.inter_finger_angles.items():
                    f.write(f"  {pair}: {angle:.1f} degrees\n")
                if report.abduction_result.abnormal_pairs:
                    f.write(f"  Abnormal pairs: {', '.join(report.abduction_result.abnormal_pairs)}\n")
                else:
                    f.write(f"  Status: All finger pairs within normal range\n")
                f.write("\n")
            
            # Opposition Test Results
            if report.opposition_result:
                f.write("THUMB OPPOSITION TEST:\n")
                f.write(f"  Successful oppositions: {', '.join(report.opposition_result.successful_oppositions) if report.opposition_result.successful_oppositions else 'None'}\n")
                if report.opposition_result.failed_oppositions:
                    f.write(f"  Failed oppositions: {', '.join(report.opposition_result.failed_oppositions)}\n")
                f.write(f"  CMC Angle: {report.opposition_result.cmc_angle:.1f} degrees\n")
                f.write("\n")
            
            # Clinical Assessment
            f.write("CLINICAL ASSESSMENT:\n")
            for flag in report.clinical_flags:
                f.write(f"  - {flag}\n")
            f.write("\n")
            
            # Recommendations
            f.write("RECOMMENDATIONS:\n")
            if report.ra_risk_score >= 70:
                f.write("  - HIGH RISK: Immediate clinical evaluation recommended\n")
                f.write("  - Consider rheumatology consultation\n")
                f.write("  - Monitor for signs of inflammation and pain\n")
            elif report.ra_risk_score >= 40:
                f.write("  - MODERATE RISK: Schedule follow-up evaluation\n")
                f.write("  - Consider physical therapy assessment\n")
                f.write("  - Regular monitoring recommended\n")
            elif report.ra_risk_score >= 20:
                f.write("  - LOW RISK: Continue routine monitoring\n")
                f.write("  - Maintain hand exercises and flexibility\n")
            else:
                f.write("  - Normal ROM maintained\n")
                f.write("  - Continue current treatment plan\n")
                f.write("  - Regular monitoring recommended\n")
        
        print(f"\nResults saved to: {summary_filename}")
        print(f"Total images captured: {len(self.captured_images)}")
    
    def print_report(self, report: ROMAnalysisReport):
        """Print report to console"""
        print("\n" + "="*60)
        print("HAND ROM ANALYSIS REPORT")
        print("="*60)
        print(f"Patient ID: {report.patient_id}")
        print(f"Test Date: {report.test_date}")
        # Risk score and flags hidden as per request
        
        if report.flexion_result:
            print("\n--- Flexion/Extension Test ---")
            for finger, angles in report.flexion_result.finger_angles.items():
                print(f"  {finger.capitalize()}: MCP={angles.mcp:.1f}° PIP={angles.pip:.1f}° DIP={angles.dip:.1f}° Total={angles.total:.1f}°")
            if report.flexion_result.abnormal_joints:
                print(f"  Abnormal joints: {', '.join(report.flexion_result.abnormal_joints)}")
        
        if report.abduction_result:
            print("\n--- Abduction/Adduction Test ---")
            for pair, angle in report.abduction_result.inter_finger_angles.items():
                print(f"  {pair}: {angle:.1f}°")
            if report.abduction_result.abnormal_pairs:
                print(f"  Abnormal pairs: {', '.join(report.abduction_result.abnormal_pairs)}")
        
        if report.opposition_result:
            print("\n--- Thumb Opposition Test ---")
            print(f"  Successful: {', '.join(report.opposition_result.successful_oppositions)}")
            if report.opposition_result.failed_oppositions:
                print(f"  Failed: {', '.join(report.opposition_result.failed_oppositions)}")
            print(f"  CMC Angle: {report.opposition_result.cmc_angle:.1f}°")
        
        print("="*60 + "\n")
    
    def run(self):
        """Main application loop"""
        cap = cv2.VideoCapture(self.video_source)
        
        if not cap.isOpened():
            print("Error: Could not open video source")
            return
        
        print("\n" + "="*60)
        print("HAND ROM ANALYZER FOR RA DETECTION")
        print("="*60)
        print("\nControls:")
        print("  SPACE - Start/Next test")
        print("  S     - Save report (when complete)")
        print("  R     - Restart test")
        print("  Q     - Quit")
        print("\nPosition your hand in front of the camera and press SPACE to begin.")
        print("="*60 + "\n")
        
        while cap.isOpened():
            ret, frame = cap.read()
            if not ret:
                break
            
            # Flip frame horizontally for mirror view
            frame = cv2.flip(frame, 1)
            h, w, _ = frame.shape
            
            # Convert to RGB for MediaPipe
            rgb_frame = cv2.cvtColor(frame, cv2.COLOR_BGR2RGB)
            results = self.hands.process(rgb_frame)
            
            # Process hand landmarks
            if results.multi_hand_landmarks:
                hand_landmarks = results.multi_hand_landmarks[0]
                
                # Draw landmarks
                frame = draw_hand_landmarks(frame, hand_landmarks, self.mp_hands, self.mp_drawing)
                
                # Extract landmarks
                landmarks = extract_landmarks(hand_landmarks)
                hand_size = get_hand_size(landmarks)
                
                # Process based on current state
                if self.current_state != TestState.WAITING and self.current_state != TestState.COMPLETE:
                    self.process_frame(landmarks, hand_size, frame)
                    
                    # Draw detection quality indicator
                    quality_color = (0, 255, 0) if self.detection_quality >= 80 else \
                                   (0, 255, 255) if self.detection_quality >= 60 else (0, 0, 255)
                    quality_text = f"Quality: {self.detection_quality:.0f}%"
                    cv2.putText(frame, quality_text, (w - 220, 30), 
                              cv2.FONT_HERSHEY_SIMPLEX, 0.7, quality_color, 2)
                    
                    # Draw stability indicator
                    is_stable = len(self.angle_stability_buffer) >= self.stability_window and \
                               self.check_angle_stability(landmarks)
                    stability_text = "STABLE" if is_stable else "STABILIZING..."
                    stability_color = (0, 255, 0) if is_stable else (0, 165, 255)
                    cv2.putText(frame, stability_text, (w - 220, 60), 
                              cv2.FONT_HERSHEY_SIMPLEX, 0.6, stability_color, 2)
                    
                    # Show posture validation message
                    if self.posture_message:
                        posture_color = (0, 255, 0) if self.posture_valid else (0, 0, 255)
                        cv2.putText(frame, self.posture_message, (10, h - 30), 
                                  cv2.FONT_HERSHEY_SIMPLEX, 0.6, posture_color, 2)
                    
                    # Show if ready to capture (requires quality, stability, AND correct posture)
                    if self.detection_quality >= 70 and is_stable and self.posture_valid:
                        ready_text = "READY - Press SPACE"
                        cv2.putText(frame, ready_text, (w - 250, 90), 
                                  cv2.FONT_HERSHEY_SIMPLEX, 0.6, (0, 255, 0), 2)
                    else:
                        if not self.posture_valid:
                            not_ready_text = "Fix posture (see bottom)"
                        elif not is_stable:
                            not_ready_text = "Hold still..."
                        else:
                            not_ready_text = "Adjust hand position"
                        cv2.putText(frame, not_ready_text, (w - 250, 90), 
                                  cv2.FONT_HERSHEY_SIMPLEX, 0.6, (0, 165, 255), 2)
                    
                    # Draw real-time angles for relevant tests
                    if self.current_state in [TestState.FIST_TEST, TestState.OPEN_HAND_BASELINE, 
                                             TestState.OPEN_HAND_RETURN, TestState.SPREAD_TEST]:
                        y_offset = 130
                        for finger_name in ['index', 'middle', 'ring', 'pinky']:
                            angles = calculate_finger_angles(landmarks, finger_name)
                            angle_text = f"{finger_name.capitalize()}: {angles.total:.0f}°"
                            cv2.putText(frame, angle_text, (10, y_offset), 
                                      cv2.FONT_HERSHEY_SIMPLEX, 0.5, (255, 255, 0), 1)
                            y_offset += 25
                
                # Show results if complete
                if self.current_state == TestState.COMPLETE:
                    report = self.generate_report()
                    frame = draw_results_summary(frame)
            
            # Draw UI
            instruction = self.get_state_instruction()
            frame = draw_test_state(frame, self.current_state, instruction)
            
            # Display frame
            cv2.imshow('Hand ROM Analyzer', frame)
            
            # Handle keyboard input
            key = cv2.waitKey(1) & 0xFF
            
            if key == ord('q'):
                break
            elif key == ord(' '):
                if self.current_state == TestState.WAITING:
                    self.advance_state()
                elif self.current_state != TestState.COMPLETE:
                    # Manual capture when SPACE is pressed
                    if results.multi_hand_landmarks:
                        hand_landmarks_for_capture = results.multi_hand_landmarks[0]
                        landmarks_for_capture = extract_landmarks(hand_landmarks_for_capture)
                        hand_size_for_capture = get_hand_size(landmarks_for_capture)
                        
                        # Check if quality AND posture are good enough
                        if self.detection_quality >= 70 and self.posture_valid:
                            # Capture image
                            self.save_frame_image(frame, self.current_state, landmarks_for_capture)
                            
                            # Capture measurements
                            if self.current_state == TestState.OPEN_HAND_BASELINE:
                                self.baseline_open_hand = landmarks_for_capture.copy()
                                print("✓ Baseline captured")
                                
                            elif self.current_state == TestState.FIST_TEST:
                                self.flexion_result = perform_flexion_test(landmarks_for_capture, hand_size_for_capture)
                                print("✓ Fist test captured")
                                
                            elif self.current_state == TestState.SPREAD_TEST:
                                self.abduction_result = perform_abduction_test(landmarks_for_capture, hand_size_for_capture)
                                print("✓ Spread test captured")
                                
                            elif self.current_state == TestState.THUMB_OPPOSITION_INDEX:
                                result = perform_opposition_test(landmarks_for_capture, hand_size_for_capture)
                                self.opposition_results['index'] = result.thumb_to_finger_distances['index']
                                print("✓ Thumb-Index opposition captured")
                                
                            elif self.current_state == TestState.THUMB_OPPOSITION_MIDDLE:
                                result = perform_opposition_test(landmarks_for_capture, hand_size_for_capture)
                                self.opposition_results['middle'] = result.thumb_to_finger_distances['middle']
                                print("✓ Thumb-Middle opposition captured")
                                
                            elif self.current_state == TestState.THUMB_OPPOSITION_RING:
                                result = perform_opposition_test(landmarks_for_capture, hand_size_for_capture)
                                self.opposition_results['ring'] = result.thumb_to_finger_distances['ring']
                                print("✓ Thumb-Ring opposition captured")
                                
                            elif self.current_state == TestState.THUMB_OPPOSITION_PINKY:
                                result = perform_opposition_test(landmarks_for_capture, hand_size_for_capture)
                                self.opposition_results['pinky'] = result.thumb_to_finger_distances['pinky']
                                # Compile full opposition result
                                self.opposition_result = perform_opposition_test(landmarks_for_capture, hand_size_for_capture)
                                print("✓ Thumb-Pinky opposition captured")
                            
                            elif self.current_state in [TestState.OPEN_HAND_RETURN, TestState.CLOSE_HAND_RETURN]:
                                print("✓ Position captured")
                            
                            # Advance to next state
                            self.advance_state()
                        elif not self.posture_valid:
                            print(f"⚠ POSTURE INCORRECT - {self.posture_message}")
                        else:
                            print(f"⚠ Quality too low ({self.detection_quality:.0f}%) - Adjust hand position")
            elif key == ord('r'):
                # Restart test
                self.current_state = TestState.WAITING
                self.flexion_result = None
                self.abduction_result = None
                self.opposition_result = None
                self.opposition_results = {}
            elif key == ord('s'):
                if self.current_state == TestState.COMPLETE:
                    report = self.generate_report()
                    self.save_report(report)
                    self.print_report(report)
        
        cap.release()
        cv2.destroyAllWindows()


# ==================== ENTRY POINT ====================

def select_camera_source():
    """Interactive camera selection menu"""
    print("\n" + "="*60)
    print("📹 CAMERA SOURCE SELECTION")
    print("="*60)
    print("\nPlease select your camera source:")
    print("\n1. 📱 DroidCam (Phone Camera via WiFi)")
    print("2. 💻 Built-in Webcam")
    print("3. 🎥 External USB Camera")
    print("4. 📁 Video File (for testing)")
    print("\n" + "="*60)
    
    while True:
        try:
            choice = input("\nEnter your choice (1-4): ").strip()
            
            if choice == "1":
                # DroidCam
                print("\n📱 Using DroidCam...")
                default_ip = "http://192.168.100.48:4747/video"
                use_default = input(f"Use default IP ({default_ip})? (y/n): ").strip().lower()
                
                if use_default == 'y':
                    video_source = default_ip
                else:
                    ip = input("Enter DroidCam IP (e.g., 192.168.1.100): ").strip()
                    port = input("Enter DroidCam port (default 4747): ").strip() or "4747"
                    video_source = f"http://{ip}:{port}/video"
                
                print(f"✓ Selected: DroidCam at {video_source}")
                return video_source
                
            elif choice == "2":
                # Built-in Webcam
                print("\n💻 Using Built-in Webcam...")
                video_source = 0
                print("✓ Selected: Built-in Webcam (index 0)")
                return video_source
                
            elif choice == "3":
                # External USB Camera
                print("\n🎥 Using External USB Camera...")
                camera_index = input("Enter camera index (usually 1 or 2): ").strip()
                try:
                    video_source = int(camera_index)
                    print(f"✓ Selected: External Camera (index {video_source})")
                    return video_source
                except ValueError:
                    print("❌ Invalid camera index. Please enter a number.")
                    continue
                    
            elif choice == "4":
                # Video File
                print("\n📁 Using Video File...")
                file_path = input("Enter video file path: ").strip()
                if file_path:
                    video_source = file_path
                    print(f"✓ Selected: Video file at {video_source}")
                    return video_source
                else:
                    print("❌ No file path provided.")
                    continue
                    
            else:
                print("❌ Invalid choice. Please enter 1, 2, 3, or 4.")
                
        except KeyboardInterrupt:
            print("\n\n❌ Camera selection cancelled.")
            exit(0)
        except Exception as e:
            print(f"❌ Error: {e}")
            continue


def main():
    """Main entry point"""
    print("\n🏥 Hand ROM Analyzer for Rheumatoid Arthritis Detection")
    print("Using MediaPipe Hand Tracking")
    
    # Interactive camera selection
    video_source = select_camera_source()
    
    print("\n" + "="*60)
    print("🚀 Starting ROM Analyzer...")
    print("="*60)
    
    # Create analyzer with selected camera source
    analyzer = HandROMAnalyzer(video_source=video_source)
    analyzer.run()


if __name__ == "__main__":
    main()
