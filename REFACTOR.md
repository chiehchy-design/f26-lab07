# REFACTOR.md

## Milestone 1: Direct a refactor, characterization first

### The pin (committed in `a8dc7f0`, before the refactor)

**The pin.** `BookingWorkflowCharacterizationTest.recurringSubmitSkipsAWeekWhoseSlotOnlyTouchesAnExistingBooking`.
A 3-week series at Mon 9:00-10:00 skips week 2 because another booking starts at
10:00 that day. The two slots only touch, yet RECURRING checks overlap with `<=`
while REGULAR and BLOCKED use `<`. The result is still accepted with the message
`"series S-1: 2 booked, 1 skipped"`, and the occurrence indices are 1 and 3 (a gap,
not renumbered).

**Why that one?** It is the rule a refactor is most likely to "fix" by accident,
by merging the three overlap checks into one helper. No shipped test covers it:
grepping the tests for `getSkipped` and `getOccurrenceIndex` finds nothing, and
the only recurring submit test uses an empty room.

**What a regeneration would do differently.** It would decide again whether
back-to-back slots conflict. It would probably follow `TimeSlot`'s "exclusive
end" javadoc and book week 2, which silently changes behavior.

### The directive

**Refactor:** Replace Conditional with Polymorphism. Each booking type gets its
own class implementing one interface (`BookingKind`), and `BookingWorkflow`
dispatches through an `EnumMap`.

**Scope:** only the `workflow/` package. `domain/`, `notify/`, `pricing/`,
`reporting/`, and all tests were off limits. The switch lives only in
`workflow/`, so nothing else needs to change.

<details>
<summary>Exact directive given to the agent (Claude Code subagent)</summary>

```text
Refactor: Replace Conditional with Polymorphism in BookingWorkflow.

Remove all four switches on BookingType (submit, cancel, priceOf, describe) by
moving each type's branch into its own class implementing one package-private
interface (BookingKind). BookingWorkflow keeps its public constructor and four
public methods unchanged, does the shared lookups it does today, and dispatches
through an EnumMap<BookingType, BookingKind>. No switch or if-chain on
BookingType may remain in the workflow package.

IN scope: only src/main/java/edu/cmu/cs214/scheduling/workflow/ (edit
BookingWorkflow.java, add package-private classes there).
OUT of scope: domain/, notify/, pricing/, reporting/, pom.xml, .github/, docs,
and every test file. No new methods on BookingType.

Behavior must not change:
- Copy each overlap check exactly. REGULAR/BLOCKED use <, RECURRING uses <=.
  Do NOT unify them; a characterization test pins it.
- RECURRING does not check member double-booking; keep it that way.
- Keep every message string, validation order, and the order of
  nextBookingId/nextSeriesId/save/publish calls.
- Keep the old default: fallbacks when the map has no entry.

Don't rename or reformat anything else. Run mvn -B test, report git status and
git diff --stat, don't commit, and list any judgment calls.
```
</details>

### The result

**The diff and the suite.** Run `git show 746c085`. It touches 6 files, all in
`workflow/`: `BookingWorkflow` plus the new `BookingKind`,
`RegularBookingKind`, `RecurringBookingKind`, `BlockedBookingKind`, and
`WorkflowContext`.
`Tests run: 36, Failures: 0, Errors: 0, Skipped: 0`, `BUILD SUCCESS` (the
shipped 35 plus my pin).

**What did NOT change.**
- **Strings:** I diffed every string literal between the old and new code, and
  they are identical.
- **Comparisons:** every `compareTo(...) < 0` / `<= 0` matches one for one, and
  the `<=` is still in `RecurringBookingKind`.
- **Files:** `git show --stat` lists only `workflow/` files, so nothing outside
  scope was touched.
- **Surprises,** still the same and not pinned by any shipped test: cancelling
  one recurring occurrence also cancels every *later* one, and `priceOf` on a
  recurring booking returns the price of the whole series.

**Looked at twice.** `BookingWorkflow` lost its `calculator` and `hub` fields,
because they moved into the per-type classes. I checked that the constructor
still null-checks all three collaborators. It does, with the same message.

### The closing explanation

**Refactor or regenerate? Refactor.**
- **Test coverage:** thin where it matters. Skipped weeks, the `<=`, cancel
  scope, and the occurrence bounds were all unpinned, so a regeneration could
  change them and stay green.
- **Code age:** young and agent-generated, which is the one point for
  regenerating. But it already encodes choices that callers may rely on.
- **Spec quality:** weak. The only spec is a README line, and the `TimeSlot`
  javadoc contradicts the code.
- **Reach:** high. Every store write and notification goes through this class.

**What would flip it.** A written spec plus tests that pin each type's rules
(overlap boundary, skip, cancel scope, price scope, messages). Then a
regeneration could be checked against them instead of trusted.

---

## Milestone 2: The pattern critique

| Pattern | Where | Problem it solves | Exists here? |
|---|---|---|---|
| Singleton | `NotifierFactory.getInstance()` | Exactly one shared stateful resource | **No.** The factory has no state and one caller (`NotificationHub.java:22`) |
| Factory | `NotifierFactory.createStrategy()` | Choosing a concrete class at run time | **No.** It always returns `EmailNotificationStrategy`, no args, no config |
| Strategy | `NotificationStrategy`, `EmailNotificationStrategy` | 2+ formats picked at run time | **No.** One implementation, and the hub can't even accept a different one |
| Observer | `NotificationHub`, `NotificationSubscriber`, `OutboxSubscriber` | A changing set of receivers the publisher doesn't know | **No.** The only `subscribe()` call is the hub subscribing itself (`NotificationHub.java:23`) |

**Simpler structure.** Keep `NotificationMessage` and `Outbox`. `NotificationHub`
becomes:

```java
public void publish(NotificationMessage m) {
    outbox.append("To: " + m.recipient() + " | Subject: " + m.subject() + " | " + m.body());
}
```

Delete the factory, the strategy, and the subscriber classes. I would keep no
interfaces, because each has one implementation and can be re-extracted when a
second one appears.

**Must still do.** Each `publish` adds exactly one line to the outbox, in order,
in the `To: … | Subject: … | body` format, and `getOutbox()` still works.
(`factoryHandsBackTheSameInstance` and `hubDeliversToItsOneSubscriber` test the
structure, not behavior. Tests can't be edited in this lab, so this stays a
proposal.)

**What would bring layers back.**
- **Strategy:** "members can get confirmations by SMS (160 chars, no subject
  line)", which needs a second format chosen per member.
- **Observer:** "also send every booking event to an audit log and Slack, with
  destinations added by config", which means several receivers that change
  over time.

**Misuse or anti-pattern?** Mostly misuse: good patterns applied to problems
this code doesn't have. The fix is to delete them until the requirement shows
up. The Singleton is closer to an anti-pattern, because it hides a global
dependency inside the hub's constructor and blocks injecting a formatter in
tests.

---

## Milestone 3: The missing pattern

**The pattern.** Strategy, as an ordered list of `PricingRule` objects, fits
`PriceCalculator`. The javadoc describes a published, ordered set of
independent pricing rules, but that order lives only as line order inside
`price()`, so adding or reordering a rule means editing the method.

**Apply it today? No.** There are only four stable rules, all pinned by
`PriceCalculatorTest`, and no requirement yet adds a rule or varies them per
room or member.
