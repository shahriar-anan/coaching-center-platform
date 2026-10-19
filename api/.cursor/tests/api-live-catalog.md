# api-live-catalog tests

PostgreSQL only. No manual check. The `*IT` class is written with the stage.

## LiveCatalogIT

- Creating a category requires both titles. A third level under a child category is stored and returned in parent order.
- A cycle in `parent` is rejected.
- Two modules in one category are both returned for that category. A module whose category is the root is returned there too.
- A staff edit leaves the module in draft. Publish before final save is rejected. After final save, publish succeeds.
- A student list omits the draft and includes the module after publish.
- A System Admin delete returns `403`. A Master Admin delete is a soft delete and the category or module disappears from later reads.
- Archiving a category that has a child and a module leaves both on the staff tree. The student list and the module read omit that branch, including a module whose own category is not archived. A new child, a new module, and a move onto the archived category are rejected. A module in another root category stays visible. Publish of a module in an archived branch is rejected.
