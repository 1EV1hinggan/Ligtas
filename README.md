# Ligtas — Campus Emergency Alert App

**OOP 101 · BSIT-2114 · Group 7**
Instructor: Sir Glenn M. Guinoban
Batangas State University – The National Engineering University, Alangilan Campus (CICS)
 
---

## 1. Project Title
**Ligtas** (Tagalog for "safe") — an Android app that sends emergency alerts to people on campus and shows the admins who has answered.

## 2. Project Description
Ligtas is a phone app for BatStateU Alangilan. A verified **administrator** picks an emergency type (earthquake, fire, and so on) and sends a ready-made alert in **English or Taglish**. Everyone in the chosen group gets a push notification that **vibrates**. They tap **one button** to say "I got it." The admin can see, live, who has answered and who hasn't, and can re-alert the people who haven't. **Teachers** can also report if everyone in their room is accounted for.

## 3. Problem Addressed
During an emergency on campus, announcements by text, group chats, or word of mouth are slow, easy to miss, and nobody knows who actually saw the message. Ligtas fixes this by:
- sending alerts only from **verified admins**, so fake alerts are not possible,
- letting the admin **see who has not answered** and alert them again,
- letting teachers **confirm their room is safe**.

## 4. Objectives
1. Let verified admins send emergency alerts quickly using ready-made bilingual templates.
2. Make sure the alert is noticed (vibrating push notification) and can be answered in one tap.
3. Re-notify people who did not answer.
4. Let teachers report whether their room is accounted for.
5. Show admins a live view of who has and has not responded.
6. Keep students **receive-only**.
7. Show the four OOP concepts (abstraction, encapsulation, inheritance, polymorphism) in real working code.
## 5. Target Users
| User | What they do in Ligtas |
|---|---|
| **Administrator** | Sends alerts, watches responses, re-alerts, sends all-clear or false-alarm |
| **Teacher** | Receives alerts, acknowledges, reports "room accounted for" |
| **Student** | Receives alerts and acknowledges (cannot send anything) |

## 6. Main Features
- Sign in with the **school Google account**; the role (Student, Teacher, Admin) comes from a roster.
- **Pre-approved alert templates**: Earthquake, Fire, Active Shooter/Lockdown, Bomb Threat, Chemical Leak, and Others.
- Alerts in **English and Taglish**.
- **Vibrating push notification** that also shows on the lock screen.
- **One-tap acknowledgment.**
- **Automatic re-alert** of people who have not answered (timer set by the admin), plus a manual "Re-alert now" button.
- **Teacher room report**: "All students accounted for", "Some students missing", or "Needs help".
- **Live status** for admins: delivered %, acknowledged %, and the names still waiting.
- **All-clear** and **False alarm** messages.
- **Alert Readiness** screen that checks the phone's settings (notifications, vibration, lock screen, Do Not Disturb, battery) so alerts really reach the user.
- **Audience choice**: send to the whole campus, one building, or a college / program / year level, and skip groups that are online that week (schedule rules for odd/even weeks, on-site, online, no classes).
- **Privacy consent notice** the first time a user signs in.
- **Server checks**: the sender must be an admin, and an admin can alert only their own campus.
- Alerts, acknowledgments and room reports are saved in the database (Firestore).
- An **admin web console** (Firebase Hosting) for managing the roster.

## 7. Technologies Used
| Part | Technology |
|---|---|
| App logic (classes, alerts, users) | **Java** |
| Screens (GUI) | **Kotlin + Jetpack Compose** *(our instructor allowed another language for the GUI)* |
| Sign-in, database, push notifications | **Firebase** (Authentication, Firestore, Cloud Messaging) |
| Small server that sends the pushes | **JavaScript (Node.js) on Vercel** — see the `sender/` folder |
| Admin web console | HTML/JavaScript on Firebase Hosting (`console/` folder) |
| Libraries | Google Credential Manager (sign-in), OkHttp (calls the sender server) |
| Tools | Android Studio, Git and GitHub |

**Why a small server?** The Firebase free plan does not run server code, and the secret key that sends pushes must never be inside the app. So the app asks the Vercel server, the server checks the person is an admin, and only then sends the push.

## 8. Team Members and Roles
| Name | Student No. | Role |
|---|---|---|
| Nicole Atienza | 25-05756 | Project Leader |
| John Edward Bathan | 25-00966 | System/Design Lead |
| Aeron Guerra | 25-05697 | Documentation |
| Levi Hinggan | 25-06830 | Lead Programmer |
 