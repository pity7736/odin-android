# Feature: Create a Category

## Overview
Users can create named categories to organize their income and expenses. With
categories in place, users can track where their money goes and identify habits
they want to improve.

## User Stories

### Create a category
As a user, I want to create a category with a name and type, so that I can
later assign it to my income and expenses and understand my spending patterns.

## Acceptance Criteria
- A category must have a name and a type (income or expense).
- A category may have an optional description.
- A category may have an optional color; if none is chosen, the app assigns one
  automatically.
- The name must not be empty.
- The name must not exceed 200 characters.
- No two categories of the same user may share the same name and type,
  regardless of capitalization. A category named "Alquiler" as expense and
  another named "Alquiler" as income are considered different categories.

## Expected Behavior

### Successful creation
- Given the user wants to create a new category
- When they provide a valid name and choose a type
- Then the category is saved and available for use with future transactions

### Name is empty
- Given the user is creating a category
- When they leave the name blank
- Then the category is not saved and they are told the name is required

### Name is too long
- Given the user is creating a category
- When they enter a name longer than 200 characters
- Then the category is not saved and they are told the name is too long

### Duplicate name and type
- Given the user already has an expense category named "Alquiler" (or
  "alquiler", "ALQUILER", etc.)
- When they try to create another expense category with the same name in any
  combination of uppercase and lowercase letters
- Then the category is not saved and they are told an expense category with
  that name already exists

### Same name with different type is allowed
- Given the user already has an expense category named "Alquiler"
- When they create an income category also named "Alquiler"
- Then the category is saved successfully

### Description is optional
- Given the user is creating a category
- When they do not provide a description
- Then the category is saved successfully without one

### Color is optional
- Given the user is creating a category
- When they do not choose a color
- Then the category is saved with an automatically assigned color

## Out of Scope
- Editing existing categories
- Deleting categories
- Listing or browsing categories
- Assigning an icon to a category
- Default categories (these are created automatically during account registration)
- Linking categories to transactions
