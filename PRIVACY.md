# Privacy policy — Burrow: Expense Tracker

_Last updated: 8 November 2026_

Burrow is built so your money stays yours.

**What Burrow collects: nothing.** Burrow never sends your data anywhere: not to us, not to anyone.
There is no account, no analytics, no ads, no crash reporting and no third-party code that talks to a
server. The F-Droid build has no internet permission at all. The Google Play and GitHub builds use the
internet only for Google Drive backup, and only if you turn it on.

**Update check (GitHub build only).** The APK from GitHub Releases asks GitHub once a day, and when you
tap Check for updates, whether a newer version exists, and downloads it if you tap Update. The request
carries nothing about you or your money; GitHub sees it like any visit to a web page.

**What stays on your phone**
- The transactions, accounts, bills, budgets, goals, people, tags and rates you enter or the app records.
- Bank and payment messages it reads to record payments (with your permission). The message text is
  deleted after 30 days; only the amount, payee and account it read are kept with the transaction.
  One-time passwords and offers are skipped and never stored.
- Receipts and bills you attach, kept in the app's private storage.

**SMS and notifications.** If you turn on auto-capture, Burrow reads incoming SMS from banks (where the
build allows it) and notifications from payment apps, only to find payments. Everything is processed on
the phone. You can turn this off at any time in More › Auto-capture or in Android settings.

**Backups.** Backups are encrypted with a password you choose (AES-256-GCM) and saved to a folder you pick.
If that folder is synced by another app (for example a cloud drive), that app's policy applies to the
encrypted file. Burrow cannot read a backup without your password, and neither can we.

**Google Drive backup (Google Play and GitHub builds, optional).** If you connect Google Drive, Burrow
saves the same encrypted backup file to a hidden folder in your own Google Drive that only Burrow can
see, by hand or on your backup schedule, and keeps the last 10. The file is encrypted with your backup
password before it leaves the phone, so Google cannot read it, and neither can we. Burrow asks Google
only for access to that hidden folder and your email address (to show which account is connected).
Disconnect stops it; Delete backups from Drive removes them.

**Rating (Google Play build).** About › Rate, and once in a while after you clear To review, show
Google Play's own rating card. Burrow sends nothing; Google Play handles it.

**Deleting your data.** More › Delete all data removes everything from the phone. Uninstalling the app
removes it too. Backups in your folder stay until you delete them.

**Children.** Burrow is not directed at children and collects no data from anyone.

**Contact.** Questions: open an issue at https://github.com/nisabmohd/burrow/issues
