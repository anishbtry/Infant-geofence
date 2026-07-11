<?php
// ============================================================
//  delete_geofence.php — Delete / deactivate geofence(s)
//  Supports two modes:
//
//  1. DELETE all active fences for a device:
//     DELETE /api/delete_geofence.php
//     Body: { "device_id": "ESP32_001" }
//
//  2. DELETE one specific fence by its id:
//     DELETE /api/delete_geofence.php
//     Body: { "device_id": "ESP32_001", "geofence_id": 7 }
//
//  Both modes mark rows as is_active = 0 (soft-delete) so that
//  the alerts history referencing the fence is preserved.
//  Pass "hard_delete": true to permanently remove the row(s).
// ============================================================
require_once 'config.php';

// Allow DELETE and POST (some HTTP clients can't send DELETE)
$method = strtoupper($_SERVER['REQUEST_METHOD']);
if (!in_array($method, ['DELETE', 'POST'])) {
    jsonResponse(['success' => false, 'error' => 'DELETE or POST required'], 405);
}

$data = getRequestBody();

// ── Validate required fields ─────────────────────────────────
if (empty($data['device_id'])) {
    jsonResponse(['success' => false, 'error' => 'device_id is required'], 400);
}

$device_id   = trim($data['device_id']);
$geofence_id = isset($data['geofence_id']) ? (int) $data['geofence_id'] : null;
$hard_delete = !empty($data['hard_delete']) && $data['hard_delete'] === true;

$db = getDB();

// ── Verify device exists ─────────────────────────────────────
$check = $db->prepare("SELECT id FROM devices WHERE device_id = :device_id");
$check->execute([':device_id' => $device_id]);
if (!$check->fetch()) {
    jsonResponse(['success' => false, 'error' => 'Device not found'], 404);
}

// ── Verify the geofence belongs to this device (if id given) ─
if ($geofence_id !== null) {
    $owns = $db->prepare("
        SELECT id FROM geofences
        WHERE id = :id AND device_id = :device_id
    ");
    $owns->execute([':id' => $geofence_id, ':device_id' => $device_id]);
    if (!$owns->fetch()) {
        jsonResponse([
            'success' => false,
            'error'   => 'Geofence not found or does not belong to this device',
        ], 404);
    }
}

// ── Perform delete ────────────────────────────────────────────
try {
    if ($hard_delete) {
        // Permanent delete ────────────────────────────────────
        if ($geofence_id !== null) {
            $stmt = $db->prepare("
                DELETE FROM geofences
                WHERE id = :id AND device_id = :device_id
            ");
            $stmt->execute([':id' => $geofence_id, ':device_id' => $device_id]);
        } else {
            $stmt = $db->prepare("
                DELETE FROM geofences WHERE device_id = :device_id
            ");
            $stmt->execute([':device_id' => $device_id]);
        }
    } else {
        // Soft delete — mark inactive (default, preserves alert history)
        if ($geofence_id !== null) {
            $stmt = $db->prepare("
                UPDATE geofences
                SET is_active = 0
                WHERE id = :id AND device_id = :device_id
            ");
            $stmt->execute([':id' => $geofence_id, ':device_id' => $device_id]);
        } else {
            $stmt = $db->prepare("
                UPDATE geofences
                SET is_active = 0
                WHERE device_id = :device_id AND is_active = 1
            ");
            $stmt->execute([':device_id' => $device_id]);
        }
    }

    $affected = $stmt->rowCount();

    if ($affected === 0) {
        jsonResponse([
            'success' => false,
            'error'   => 'No active geofence found to delete',
        ], 404);
    }

    jsonResponse([
        'success'      => true,
        'message'      => $hard_delete
            ? 'Geofence permanently deleted'
            : 'Geofence deactivated',
        'device_id'    => $device_id,
        'geofence_id'  => $geofence_id,
        'rows_affected' => $affected,
        'hard_delete'  => $hard_delete,
    ]);

} catch (PDOException $e) {
    jsonResponse([
        'success' => false,
        'error'   => 'Database error: ' . $e->getMessage(),
    ], 500);
}
