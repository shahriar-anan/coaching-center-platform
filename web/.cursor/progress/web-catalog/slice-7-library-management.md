# web-catalog — slice 7 (library management)

## Done

- Book list shows both titles, the category, and Available or Unavailable. Price is Master Admin only. Each row opens the book.
- Create saves the book, then opens Details. Details is two columns: fields on the left, one category and the cover on the right.
- The view shows the cover, titles, author, availability, description, and one Placed in chip. English title and Bangla title switch that name. There is no content step and no category tree.
- Delete book is on the toolbar. Delete category is on Details. Both are Master Admin only.

## Tested

- `npx tsc --noEmit` passed.
- `eslint` on the library screens passed.
- No mocked book-management test. Shahriar checks these screens in the browser exit.

## Next

Browser exit, after the API is restarted so `V8__book_categories.sql` is applied.
