# Feature: Spending by category

## Overview
Recording expenses is only half the job: the user also needs to see where their
money goes. This report shows how much the user spent in each category over a
period they choose, starting with the current month so far, so they can spot
where most of their money went and adjust their spending.

## User Stories

### See where my money went this month
As a user, I want to see how much I have spent in each category since the first
day of the current month, so that I know where my money is going while the month
is still running.

### Choose the period
As a user, I want to choose the start and end dates of the report, so that I can
review any past period, such as last month or the whole year.

### See each currency on its own
As a user with accounts in more than one currency, I want to see the spending of
one currency at a time, so that amounts in different currencies are never added
together.

### Compare categories at a glance
As a user, I want to see each category's share of my total spending as a
picture as well as numbers, so that I can tell at a glance which categories
weigh the most.

## Acceptance Criteria

### Where the report is
- The report is reached from its own entry, "Reportes", in the navigation bar,
  next to "Inicio", "Cuentas" and "Categorías".
- The report is titled "Gastos por categoría".

### What counts as spending
- Every expense counts, whatever the account it was recorded on: money accounts
  (savings and cash) and credit cards alike.
- An expense counts under its own category, on its own date. A credit card
  expense counts on the date the purchase was made, not when the card is paid.
- Payments to a credit card do not count as spending: they move money between the
  user's own accounts, and the purchases they pay for already count.
- Transfers between the user's own accounts do not count as spending.
- Incomes are not part of the report.

### The period
- When the report opens, the period runs from the first day of the current month
  to today.
- The user can change the start date and the end date.
- Both dates are included: a period from October 1 to October 7 counts the
  expenses dated October 1 and the expenses dated October 7.
- The end date cannot be earlier than the start date: such a period cannot be
  chosen.
- Neither date can be later than today: such a date cannot be chosen.
- There is no limit on how far back the start date can go.

### Currencies
- The report shows the spending of one currency at a time. Amounts in different
  currencies are never added together and never converted.
- When the user's accounts use more than one currency, the report offers one
  choice per currency used by the user's accounts, in the order COP, USD, EUR.
  The currencies offered do not depend on the chosen period.
- When all the user's accounts use the same currency, no currency choice is
  offered.
- When the report opens, it shows COP. If the user has no account in COP, it
  shows the first currency in the order COP, USD, EUR that the user's accounts
  use.
- Changing the period keeps the chosen currency.

### What the report shows
- The total spent in the chosen currency during the period, labeled "Total
  gastado".
- One entry per category with spending in the chosen currency during the period,
  showing the category's name, its total, and its share of the total spent.
- A circle chart in which each category's slice is as large as its share of the
  total spent, drawn in the category's own color, so each slice matches the
  category's color everywhere else in the app.
- Category names are shown with their first letter capitalized, as everywhere
  else in the app: a category named "comida" is shown as "Comida".
- Categories with no spending in the chosen currency during the period are not
  shown.
- Categories are ordered from the highest total to the lowest. Categories with
  the same total are ordered alphabetically, ignoring uppercase/lowercase and
  accents. "ñ" is its own letter, not an accented "n", and comes right after
  "n": "mono", "Moño", "mozo".
- Amounts are shown in full, in the app's usual money format: never shortened
  (e.g. never "$1,25M"), with dot thousand separators and two decimals after a
  comma, e.g. $1.250.000,00.

### Shares
- Each share is shown as a whole percentage, e.g. "42%". A share exactly halfway
  between two whole percentages rounds up: 16,5% shows as "17%".
- Each share is rounded on its own. Shares are never adjusted to make them add up
  to exactly 100%, so the shown percentages may add up to 99% or 101%.
- A category whose share rounds to 0% shows "<1%" instead.

### No spending in the period
- When there are no expenses in the chosen currency during the period, the report
  shows the message "Sin gastos en <currency> en estas fechas." (e.g. "Sin gastos
  en USD en estas fechas.") and the hint "Cambia el rango de fechas para ver tus
  gastos por categoría.", in place of the total, the chart and the categories.

### The report cannot be loaded
- When the report cannot be loaded for a technical reason, the message "Error al
  cargar el reporte" is shown in place of the report.

### Returning to the report
- Each time the user returns to the report from another part of the app, it opens
  again with the period from the first day of the current month to today and the
  starting currency, as if opened for the first time.

### Messages
- All user-facing text is shown in Spanish.

## Expected Behavior

### Spending of the current month by category
- Given today is October 7
- And the user recorded, in COP, an expense of $1.800.000,00 in "Arriendo" on
  October 1, an expense of $800.000,00 in "Mercado" on October 3 and an
  expense of $450.000,00 in "Mercado" on October 6
- When the user opens the report
- Then the period shown is October 1 to October 7
- And "Total gastado" is $3.050.000,00
- And "Arriendo" shows $1.800.000,00 and 59%
- And "Mercado" shows $1.250.000,00 and 41%
- And "Arriendo" is listed before "Mercado"

### Expenses outside the period are not counted
- Given today is October 7
- And the user recorded an expense of $200.000,00 in "Mercado" on September 30
- And an expense of $100.000,00 in "Mercado" on October 2
- When the user opens the report
- Then "Mercado" shows $100.000,00

### Choosing another period
- Given the user recorded an expense of $300.000,00 in "Ocio" on September 15
- And an expense of $50.000,00 in "Ocio" on October 2
- When the user opens the report and chooses the period September 1 to
  September 30
- Then "Total gastado" is $300.000,00
- And "Ocio" shows $300.000,00

### Both ends of the period are included
- Given the user recorded an expense in "Transporte" on September 1 and another
  in "Transporte" on September 30
- When the user chooses the period September 1 to September 30
- Then both expenses count in the "Transporte" total

### The end date cannot be earlier than the start date
- Given the user has chosen September 10 as the start date
- When the user chooses the end date
- Then dates earlier than September 10 cannot be chosen

### Future dates cannot be chosen
- Given today is October 7
- When the user chooses the start or end date
- Then dates after October 7 cannot be chosen

### Credit card expenses count on their purchase date
- Given the user recorded an expense of $120.000,00 in "Restaurantes" on the
  credit card "Visa" on October 3
- And paid $120.000,00 to "Visa" from the savings account "Ahorros" on October 5
- When the user opens the report on October 7
- Then "Restaurantes" shows $120.000,00
- And "Total gastado" is $120.000,00, not $240.000,00

### Transfers are not spending
- Given the user transferred $500.000,00 from "Ahorros" to "Efectivo" on October 2
- And recorded no expenses in October
- When the user opens the report on October 7
- Then the report shows that there are no expenses in COP in these dates

### Expenses from every account are added together
- Given the user recorded an expense of $40.000,00 in "Mercado" from
  "Efectivo", an expense of $60.000,00 in "Mercado" from "Ahorros" and an
  expense of $100.000,00 in "Mercado" on the credit card "Visa", all in COP and
  in October
- When the user opens the report on October 7
- Then "Mercado" shows $200.000,00

### Each currency is shown on its own
- Given the user has a COP account and a USD account
- And recorded an expense of $1.250.000,00 in "Mercado" from the COP account and an
  expense of US$120,00 in "Viajes" from the USD account, both in October
- When the user opens the report on October 7
- Then the choices COP and USD are offered, in that order
- And COP is chosen
- And "Total gastado" is $1.250.000,00, with only "Mercado" listed
- When the user chooses USD
- Then "Total gastado" is US$120,00, with only "Viajes" listed

### No currency choice with a single currency
- Given all the user's accounts are in COP
- When the user opens the report
- Then no currency choice is offered

### The report starts in the first currency when there is no COP account
- Given the user's accounts are in USD and EUR only
- When the user opens the report
- Then the choices USD and EUR are offered, in that order
- And USD is chosen

### Currency choices do not depend on the period
- Given the user has a COP account and a USD account
- And recorded expenses in COP during October but nothing in USD
- When the user opens the report on October 7
- Then the choices COP and USD are both offered

### The chosen currency stays when the period changes
- Given the user has a COP account and a USD account
- And recorded an expense in USD in October and none in September
- And the user has chosen USD
- When the user changes the period to September 1 to September 30
- Then USD is still chosen
- And the report shows the message "Sin gastos en USD en estas fechas."

### No expenses in the period
- Given the user recorded no expenses in COP from October 1 to October 7
- When the user opens the report on October 7
- Then the report shows the message "Sin gastos en COP en estas fechas." and the
  hint "Cambia el rango de fechas para ver tus gastos por categoría."
- And no total, chart or category is shown

### Categories without spending are not shown
- Given the user has the categories "Mercado" and "Ocio"
- And recorded expenses only in "Mercado" in October
- When the user opens the report on October 7
- Then "Mercado" is listed
- And "Ocio" is not listed

### Categories with the same total are ordered alphabetically
- Given the user spent $100.000,00 in "Transporte", $100.000,00 in "Álamo" and
  $100.000,00 in "comida" in October
- When the user opens the report on October 7
- Then the categories are listed as "Álamo", "Comida", "Transporte", in that
  order

### "ñ" is ordered right after "n" among equal totals
- Given the user spent $100.000,00 in "Zapatos", $100.000,00 in "Ñame" and
  $100.000,00 in "Nevera" in October
- When the user opens the report on October 7
- Then the categories are listed as "Nevera", "Ñame", "Zapatos", in that order

### Each slice uses its category's color
- Given the category "Mercado" has an orange color and "Arriendo" a blue color
- And the user spent in both categories in October
- When the user opens the report on October 7
- Then the slice for "Mercado" is orange and the slice for "Arriendo" is blue
- And each category's entry shows its own color next to its name

### Shares are rounded on their own
- Given the user spent $134.000,00 in "Arriendo", $33.000,00 in "Mercado" and
  $33.000,00 in "Ocio" in October
- When the user opens the report on October 7
- Then "Arriendo" shows 67%, "Mercado" shows 17% and "Ocio" shows 17%

### A tiny share shows "<1%"
- Given the user spent $1.000.000,00 in "Arriendo" and $3.000,00 in "Dulces" in
  October
- When the user opens the report on October 7
- Then "Dulces" shows $3.000,00 and "<1%"

### The report cannot be loaded
- Given the user's expenses cannot be read for a technical reason
- When the user opens the report
- Then the message "Error al cargar el reporte" is shown
- And no total, chart or category is shown

### Returning to the report starts fresh
- Given the user has a COP account and a USD account
- And the user chose USD and the period September 1 to September 30
- When the user goes to "Cuentas" and comes back to "Reportes"
- Then the period is again the first day of the current month to today
- And COP is chosen

## Out of Scope
- Incomes: no income by category and no income compared with spending.
- Spending per category month by month.
- Narrowing the report by tags.
- Seeing the list of expenses behind a category from the report.
- A combined total across currencies, or converting between currencies.
- Comparing with a previous period (e.g. "+12% vs. last month").
- Budgets or spending limits per category.
- Exporting or sharing the report.
- Remembering the chosen period or currency after leaving the report.
- Keeping two categories from showing the same color.
