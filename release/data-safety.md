# Play Console › Data safety answers

- Does your app collect or share any of the required user data types? **No.**
- Is all of the user data collected by your app encrypted in transit? **Not applicable (no data leaves the device).**
- Do you provide a way for users to request that their data is deleted? **Yes**: More › Delete all data; uninstalling removes everything.
- Data shared with third parties: **None.** Data collected: **None.**
- Notes for reviewers: the app has no INTERNET permission (check the merged manifest). Financial info,
  SMS and notification content are processed only on the device and never transmitted.
