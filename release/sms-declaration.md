# Play Console › Permissions declaration (SMS)

**Permissions:** RECEIVE_SMS, READ_SMS (in every build, the play flavour too since 0.1.0 code 3).

**Core functionality:** Burrow is an expense tracker whose main feature is recording payments
automatically from bank transaction SMS ("Rs.240 debited from A/c XX1234 to …"). Without SMS access the
app cannot record card and bank payments that are not made through a payment app.

**Permitted use case:** SMS-based money management (expense tracking from transaction messages).

**How it's used:** messages from banks are matched against message rules on the device to read the
amount, payee and account. OTPs, offers and failed transactions are skipped. Message text is deleted
after 30 days. Nothing is uploaded: the app has no INTERNET permission.

**User control:** off until the user turns it on in the Auto-capture intro; can be turned off at any time.

**Video:** record: open More › Auto-capture › Try it; a sample bank SMS arrives, the notification asks
for a category, one tap saves it.
