# Feature: List Categories

## Overview
The user can see all their categories and filter them by type. This makes it
easy to review what categories exist and ensures that when recording income or
an expense, only the relevant categories are shown.

## User Stories

### Browse all categories
As a user, I want to see all my categories, so that I know what categories I
have available.

### Filter categories by type
As a user, I want to filter categories by type, so that when I am recording
income or an expense I only see the categories that apply.

### Search categories by name
As a user, I want to search categories by name, so that I can quickly find a
specific category.

### View category details
As a user, I want to open a category to see its details, so that I can review
the information associated with it.

## Acceptance Criteria
- Each category in the list shows its name and type.
- The list can be filtered to show only income categories, only expense
  categories, or all categories.
- The user can search categories by name; the search applies within the active
  filter (or across all categories when no filter is active).
- When no categories match the active filter and search, an empty state is shown.
- When no categories exist at all, an empty state is shown.
- Selecting a category opens the category details screen, which shows the
  category name.

## Expected Behavior

### All categories shown
- Given the user has at least one category
- When they open the category list with no filter applied
- Then all categories are shown, each with its name and type

### Filter by income
- Given the user has at least one income category
- When they filter the list by income
- Then only income categories are shown

### Filter by expense
- Given the user has at least one expense category
- When they filter the list by expense
- Then only expense categories are shown

### No categories match the filter
- Given the user has no categories of a particular type
- When they filter the list by that type
- Then an empty state is shown indicating there are no categories of that type

### No categories exist
- Given the user has not created any categories
- When they open the category list
- Then an empty state is shown indicating there are no categories

### Search within all categories
- Given the user has not applied a type filter
- When they type a name or partial name in the search field
- Then only categories whose names contain that text are shown

### Search within filtered categories
- Given the user has filtered the list by type
- When they type a name or partial name in the search field
- Then only categories of that type whose names contain that text are shown

### No results for search
- Given the user has entered a search term
- When no category name matches that term within the active filter
- Then an empty state is shown

### Open category details
- Given the user can see a category in the list
- When they select it
- Then the category details screen opens showing the category name

## Out of Scope
- Editing categories
- Deleting categories
- Showing category color or description in the list
- Sorting categories
