/// Maps a stable API `code` to a catalog key. Unknown codes use the generic string.
String errorMessageKey(String code) {
  switch (code) {
    case 'INVALID_CREDENTIALS':
      return 'errors.invalidCredentials';
    case 'LOGIN_LOCKED':
      return 'errors.loginLocked';
    case 'INVALID_CODE':
      return 'errors.invalidCode';
    case 'CODE_EXPIRED':
      return 'errors.codeExpired';
    case 'CODE_ALREADY_USED':
      return 'errors.codeAlreadyUsed';
    case 'CODE_ATTEMPTS_EXCEEDED':
      return 'errors.codeAttemptsExceeded';
    case 'DUPLICATE_PHONE':
      return 'errors.duplicatePhone';
    case 'DUPLICATE_EMAIL':
      return 'errors.duplicateEmail';
    default:
      return 'errors.generic';
  }
}
