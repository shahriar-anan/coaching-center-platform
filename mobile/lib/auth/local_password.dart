enum LocalPasswordIssue { none, length, mismatch }

/// Confirm-password and length stay on the device. The confirm value is never sent.
LocalPasswordIssue checkPassword(String password, String confirm) {
  if (password.length < 8 || password.length > 72) {
    return LocalPasswordIssue.length;
  }
  if (password != confirm) {
    return LocalPasswordIssue.mismatch;
  }
  return LocalPasswordIssue.none;
}
