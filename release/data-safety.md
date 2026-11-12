# Play Console › Data safety answers

- Does your app collect or share any of the required user data types? **No.**
- Is all of the user data collected by your app encrypted in transit? **Not applicable (no data leaves the device).**
- Do you provide a way for users to request that their data is deleted? **Yes**: More › Delete all data; uninstalling removes everything.
- Data shared with third parties: **None.** Data collected: **None.**
- Optional Google Drive backup (2026-11-08): the user's own data goes, encrypted with their own password
  before upload, to a hidden app folder in their own Drive. Data the developer can access: none. Answer as
  in Weft's PLAY-DATA-SAFETY.md (user-initiated transfer to the user's own storage, encrypted in transit).
- Notes for reviewers: INTERNET is used only by Google Drive backup (check the merged manifest). Financial info,
  SMS and notification content are processed only on the device and never transmitted.
