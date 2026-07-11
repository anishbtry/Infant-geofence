# Infant Geofence - IoT Based Tracking System

## About The Project

Infant Geofence is an IoT-based tracking system designed to help caregivers monitor an infant's location in real time. The project uses a GPS-enabled hardware tracker that collects location data and sends it to a backend server. The location can then be viewed through an Android application.

The main purpose of this project is to provide a simple and reliable way to track an infant's movement, monitor safe areas using geofencing, and receive alerts when the tracker moves outside a defined zone.

The system combines IoT hardware, a PHP backend, MySQL storage, and an Android application to create a complete tracking solution.

---

## Features

- Real-time location tracking
- View tracker location on a map
- Geofence-based safe zone monitoring
- Alerts when the tracker leaves or enters a safe area
- Location history tracking
- Device connection status monitoring
- Battery status monitoring
- Simple and user-friendly Android interface

---

## How The System Works

The system is divided into three main parts:

### IoT Tracker

The hardware tracker is built using an ESP32 microcontroller and a NEO-6M GPS module.

The GPS module collects the current location coordinates, and the ESP32 processes the data before sending it to the backend server.

**Hardware components used:**

- ESP32 Microcontroller
- NEO-6M GPS Module
- Battery supply
- Breadboard
- Power switch
- Switch box
- LED indicators for connection status

---

### Backend

The backend is developed using PHP and runs on a WAMP server environment.

It acts as a bridge between the IoT device and the Android application by handling data communication and API requests.

The backend is responsible for:
- Receiving location data from the tracker
- Sending location information to the mobile application
- Managing tracker and geofence-related requests

---

### Android Application

The mobile application is developed using Kotlin with XML layouts in Android Studio.

The application allows users to:
- Track the device location
- View the tracker on a map
- Set and manage geofence areas
- Receive alerts
- Check device information

---

## Technologies Used

### Android App
- Kotlin
- XML
- Android Studio

### Backend
- PHP
- WAMP Server
- REST APIs

### Hardware
- ESP32
- NEO-6M GPS Module
- Battery
- Breadboard
- Switch
- LED Indicators

### Maps
- Free Map API

---

## Project Workflow

1. The GPS module collects the tracker's current location.
2. The ESP32 receives and processes the GPS data.
3. The location information is sent to the PHP backend.
4. The backend handles the requests and communicates with the Android application.
5. The Android app displays the tracker's location and provides tracking features.

---

## Setup Instructions

### Android Application

1. Clone this repository:

```bash
git clone https://github.com/anishbtry/infant-geofence.git
```

2. Open the project in Android Studio.

3. Configure the backend API URL.

4. Build and run the application on an Android device.

---

### Backend Setup

1. Install WAMP Server.
2. Place the PHP backend files inside the WAMP directory.
3. Start Apache and MySQL services.
4. Configure the backend connection.
5. Connect the Android application with the backend API.

---

## Future Improvements

Some improvements planned for the future:

- Cloud-based deployment
- Better GPS accuracy
- Push notifications
- Improved battery performance
- Smaller and more compact hardware design
- Support for multiple tracking devices
- Danger Zones

---

## License

This project was developed as an IoT-based tracking solution for educational purposes.
