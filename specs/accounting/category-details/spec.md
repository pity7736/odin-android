# Feature: Category Details

## Overview

When a user selects a category from the category list, they can view all of its
information in one place. This gives the user a clear, complete picture of any
category they have created.

## User Stories

### View category details
As a user, I want to see all the information about a category, so that I can
review its name, type, description, color, and creation date at a glance.

## Acceptance Criteria

- The user can open the details of any category from the category list.
- All category information is displayed: name, type (income or expense),
  description, color, and creation date.
- The color is shown as a filled circle, consistent with how it appears in the
  category list.
- The creation date is shown in full format (e.g., "14 de septiembre de 2026").
- The user can return to the category list using the standard back action.
- If the category cannot be found, an error message is displayed and the user
  can go back.

## Expected Behavior

### Viewing a category with all fields
- Given the user has a category with a name, type, description, color, and
  creation date
- When the user selects that category from the category list
- Then the app shows the category details with all of its information displayed

### Viewing a category without a description
- Given the user has a category with no description
- When the user selects that category from the category list
- Then the app shows the category details with all information except description

### Category not found
- Given the user selects a category that no longer exists
- When the details are loaded
- Then the app shows an error message indicating the category was not found
- And the user can go back to the category list

### Navigating back
- Given the user is viewing category details
- When the user uses the standard back action
- Then the app returns to the category list

## Out of Scope

- Editing category information from the details view.
- Deleting a category from the details view.
- Showing statistics such as total spent or number of transactions.
