/// Client hints for catalog outline and file rows. Bytes and lecture content still
/// come from the API routes; these flags only drive lock vs play affordances.
class CatalogAccess {
  static bool lectureAppearsOpen({
    required String accessType,
    required bool lectureFree,
    required bool enrolled,
    required bool enrollmentKnown,
  }) {
    if (accessType == 'FULL_FREE' || lectureFree) {
      return true;
    }
    if (enrollmentKnown && enrolled) {
      return true;
    }
    return false;
  }

  static bool courseMaterialAppearsOpen({
    required String accessType,
    required bool materialFree,
    required bool enrolled,
    required bool enrollmentKnown,
  }) {
    if (accessType == 'FULL_FREE' || materialFree) {
      return true;
    }
    if (enrollmentKnown && enrolled) {
      return true;
    }
    return false;
  }
}
