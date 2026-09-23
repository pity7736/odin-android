# Feature: Keeping the edited field visible above the keyboard

## Overview
Whenever a user fills in a form, the field they are editing must stay visible
while the on-screen keyboard is open. This is a shared behavior used by every
form in the app, so all of them keep the active field in view the same way and a
user is never left typing into a field they cannot see.

## User Stories

### See the field I am filling in
As a user, I want the field I am currently editing to stay visible while the
keyboard is open, so that I can see what I am typing instead of typing blindly
behind the keyboard.

### Keep seeing each field as I move through a form
As a user, I want the form to keep showing the field I move to next, so that I
can fill in a long form from top to bottom without any field being hidden by the
keyboard.

## Acceptance Criteria
- This behavior applies to every form in the app where a user enters
  information — for example logging in, registering, and creating an account —
  with no exceptions. All of them keep the active field visible the same way.
- When the keyboard opens, the form makes room for it and brings the field the
  user is editing into the space that stays visible above the keyboard.
- The visible field is not flush against the top edge of the keyboard: a small
  amount of space is kept between the field and the keyboard so the field is
  comfortably readable.
- When a field is already fully visible above the keyboard, the form leaves it
  where it is and does not scroll unnecessarily.
- Each time the user moves to another field, the form again brings that
  newly-active field into the visible area above the keyboard.
- While the keyboard is open, the user can still move through the rest of the
  form to reach any other field.
- When the keyboard closes, the form returns to using the full height of the
  screen again.

## Expected Behavior

### A field near the bottom is brought above the keyboard
- Given a user is on any form with a field near the bottom of the screen
- When the user starts editing that field and the keyboard opens
- Then the form makes room for the keyboard and moves the field into the visible
  area above it
- And a small amount of space is kept between the field and the top of the
  keyboard

### A field already in view is left in place
- Given a user is on any form with a field near the top of the screen
- When the user starts editing that field and the keyboard opens
- Then the field stays visible and the form does not scroll it away

### Moving to another field brings it into view
- Given a user is editing a field on any form with the keyboard open
- When the user moves to another field that would be hidden behind the keyboard
- Then the form brings that newly-active field into the visible area above the
  keyboard
- And a small amount of space is kept between it and the top of the keyboard

### Other fields remain reachable while the keyboard is open
- Given a user is editing a field on any form with the keyboard open
- When the user moves through the rest of the form
- Then the user can reach every other field while the keyboard stays open

### Closing the keyboard restores the full form
- Given a user is editing a field on any form with the keyboard open
- When the user closes the keyboard
- Then the form returns to using the full height of the screen

### Every form behaves the same way
- Given a user is filling in any form in the app, such as logging in,
  registering, or creating an account
- When the keyboard opens over a field near the bottom
- Then every one of those forms keeps the active field visible above the keyboard
  the same way

## Out of Scope
- The visual design and layout of the forms themselves — this feature only keeps
  the active field visible and changes nothing about how the forms look.
- Which fields a form has, whether a field is required, and any checking of what
  the user enters.
- Which kind of keyboard is shown for a given field, and what the keyboard's
  action key does.
