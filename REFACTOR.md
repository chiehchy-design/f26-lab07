# REFACTOR.md

## Milestone 1: Direct a refactor, characterization first

### The pin (committed in `a8dc7f0`, before the refactor)

**The pin.** I added a characterization test on how a **recurring booking
handles a week whose slot is already taken**
(`BookingWorkflowCharacterizationTest.recurringSubmitSkipsAWeekWhoseSlotOnlyTouchesAnExistingBooking`),
because no shipped test covers that case today.

- **The feature:** when you book a weekly series, `submit` checks each week for
  a conflict in the room. If a week is taken, it skips that week and books the
  rest. Unlike a regular booking, the series also counts a booking that merely
  *touches* its slot as a conflict: a slot ending at 10:00 conflicts with one
  starting at 10:00. That's because RECURRING compares with `<=`, while REGULAR
  and BLOCKED compare with `<`.
- **The test:** someone already holds the room on Mon Oct 12 from 10:00 to
  11:00. Then I book a 3-week series every Monday from 9:00 to 10:00, starting
  Oct 5. Week 2 (Oct 12, 9:00-10:00) only touches the existing booking and
  doesn't overlap it.
- **The result:** week 2 is skipped, and weeks 1 and 3 are booked. The
  request is still accepted, with the message `"series S-1: 2 booked, 1 skipped"`.
  The two bookings are numbered occurrence 1 and occurrence 3, so the gap is
  kept rather than renumbered. Three notifications are sent. The test passes
  on the original code.

**Why that one?** It is the rule a refactor is most likely to break by
accident. The three booking types each have their own overlap check, and a
refactor would naturally merge them into one helper. That would silently start
booking week 2. No shipped test catches it: the only recurring submit test uses
an empty room, and no test checks skipped weeks (a grep for `getSkipped` and
`getOccurrenceIndex` finds nothing).

### The directive

**Refactor:** Replace Conditional with Polymorphism.

- **The problem:** `BookingWorkflow.java` has four methods: `submit`, `cancel`,
  `priceOf`, and `describe`. Each one does the same thing first: it switches on
  the booking type (REGULAR, RECURRING, BLOCKED) and runs different code for
  each type. So the same type check is repeated four times, and adding a new
  booking type would mean editing all four switches.
- **The fix:** pull out what differs by type. A new interface, `BookingKind`,
  has one method for each of those four operations. Each booking type gets its
  own class implementing it: `RegularBookingKind`, `RecurringBookingKind`, and
  `BlockedBookingKind`. Each class holds that type's code from all four
  methods, copied over unchanged.
- **After:** `BookingWorkflow` holds the interface. It keeps a map from booking
  type to `BookingKind`, looks up the right one, and calls it, so no `switch`
  is left. Its four public methods keep exactly the same signatures, so
  everything that calls the workflow stays the same.

**Scope:** only the `workflow/` package (the agent edited `BookingWorkflow.java` and
added the new classes there). `domain/`, `notify/`, `pricing/`, `reporting/`,
and all tests were off limits. The repeated switch only exists in
`workflow/`, so nothing else needed to change.

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
- **Test coverage:** the tests are green, but they miss important behavior.
  No test checked what happens when a series week is already taken (my pin is
  the first). No test checks that cancelling one recurring occurrence also
  cancels all later ones. No test checks that a series must be 1 to 26 weeks.
  A regenerated class could do any of these differently and still pass every
  test, so the tests can't tell us whether a regeneration broke anything. A
  refactor moves the existing code without rewriting it, so those behaviors
  stay the same.
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
