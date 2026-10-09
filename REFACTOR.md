# REFACTOR.md

One section per milestone. Fill each one in as you go, in order.

Milestone 1 is written in two sittings, the pin before the refactor and the
rest after. A pin written afterwards is worth nothing, and a TA will ask.

Keep it short and specific. Point at methods, call sites, and test names.

---

## Milestone 1: Direct a refactor, characterization first

### The pin (write this section before you direct the refactor)

**The pin.** File and test name, plus one sentence naming the method and the
observable result it pins. Not "recurring bookings work". Green against the
shipped code, and you did not edit or delete an existing test method to get
there.

> `src/test/java/edu/cmu/cs214/scheduling/workflow/BookingWorkflowCharacterizationTest.java`,
> `recurringSubmitSkipsAWeekWhoseSlotOnlyTouchesAnExistingBooking`. It pins that
> `BookingWorkflow.submit` on a 3-week RECURRING request (Mon 09:00-10:00) skips
> week 2 because a booking already starts at 10:00 that Monday. The slots only
> touch and do not overlap, but the series uses the inclusive `<=` comparison
> while REGULAR and BLOCKED use `<`. The pinned outcome is: still accepted,
> `getSkipped()` is exactly that week-2 slot, message
> `"series S-1: 2 booked, 1 skipped"`, the two written occurrences have
> `occurrenceIndex` 1 and **3** (the gap stays, nothing is renumbered), and the
> outbox holds 3 messages. It is a new test class, green against the shipped
> code (36 run, 0 failures), and no existing test method was edited.

**Why that one, and does a shipped test already cover it?** Of everything
`BookingWorkflow` does, why is this the behavior worth a test? If something
shipped comes close, say what your pin adds. If nothing does, say how you
checked.

> This is the most surprising rule in the class, and the one a refactor is most
> likely to "fix" by accident. A replace-conditional refactor will want to pull
> the three copies of the overlap check into one shared helper. Two use `<` and
> one uses `<=`, so merging them quietly changes which weeks a series skips.
> It also pins the partial-success path (`BookingOutcome.series` with a
> non-empty skipped list) and the occurrence-index numbering.
> I checked the shipped tests by reading all 18 in `BookingWorkflowTest` and
> grepping the test tree for `getSkipped` and `getOccurrenceIndex`. Neither one
> appears. The only recurring submit test,
> `recurringSubmitBooksEveryWeekOfAnOpenSeries`, uses an empty room, so nothing
> is ever skipped. `regularSubmitAcceptsASlotThatStartsWhenAnotherEnds` pins the
> opposite boundary rule, but only for REGULAR.

**What a regeneration would do differently here.** Suppose someone
threw this class away and regenerated it from a one-line description of what a
booking workflow does. Name the decision that would be made a second time, and
say which way it would probably go.

> The decision is whether back-to-back slots conflict. TimeSlot's own javadoc
> says "inclusive start and exclusive end", so a regeneration would almost
> certainly write a single half-open overlap check (`<`) for every type. Week 2
> would then be **booked**, not skipped. That is arguably more correct, but it
> is a behavior change that nobody signed off on: today a series leaves a
> buffer next to existing bookings. A regeneration would also have to decide
> again whether a series fails as a whole or skips taken weeks one at a time,
> and whether occurrence indices are renumbered after a skip.

### The directive

**The refactor and the exact directive.** Name the refactor (one from the menu
in the handout) and paste the directive you gave the agent, including the scope
you set, meaning which files and packages were in bounds, which were not, and
one line on why the boundary sits where it does.

> **Refactor: Replace Conditional with Polymorphism.** I gave this directive
> to a Claude Code subagent (Claude Opus 5.5) word for word:
>
> ```text
> Refactor: Replace Conditional with Polymorphism in BookingWorkflow.
>
> Goal: BookingWorkflow.java switches on BookingType in four methods (submit,
> cancel, priceOf, describe). Remove all four switches by moving each type's
> branch into its own class that implements one package-private interface
> (e.g. BookingKind with submit/cancel/price/describe methods). BookingWorkflow
> keeps its public constructor and its four public methods with identical
> signatures, does the shared lookups it does today (null request check, unknown
> room, unknown booking, room-name fallback), and dispatches by the
> booking/request type through an EnumMap<BookingType, BookingKind> built in the
> constructor. There must be no switch or if-chain on BookingType left in the
> workflow package after the refactor (the map lookup is the only dispatch).
>
> Scope, IN bounds: only src/main/java/edu/cmu/cs214/scheduling/workflow/. You
> may edit BookingWorkflow.java and add new package-private classes in that
> package (one per booking type, plus the interface; a small package-private
> shared context/helper is fine if it holds only what is genuinely shared, like
> store/calculator/hub and FACILITIES_CONTACT and recipientFor).
>
> Scope, OUT of bounds: do not touch anything under domain/, notify/, pricing/,
> reporting/, pom.xml, .github/, README/SETUP/REFACTOR.md, and do not edit, add,
> or delete any test file. Do not add a type method to BookingType or any other
> domain class.
>
> Behavior must not change, byte for byte. In particular:
> - Copy each overlap check exactly as written. REGULAR and BLOCKED use strict
>   <; RECURRING uses inclusive <=. Do NOT unify these into a shared helper, and
>   do not "fix" the inconsistency. A characterization test pins it.
> - RECURRING does not check member double-booking; REGULAR does. Keep it that way.
> - Keep every rejection message, notification recipient/subject/body string,
>   outcome message, and the order of store.nextBookingId()/nextSeriesId()
>   calls, store.save, and hub.publish exactly as today.
> - Keep the order of validation checks in each branch.
> - The current default: branches return "unsupported booking type " + type /
>   false / 0.0 / "Booking #id in roomName". Preserve that as the fallback
>   when the map has no entry.
> - Keep existing javadoc on the public methods.
>
> Do not rename, reformat, or "improve" anything else. Match the existing code
> style. When done: run mvn -B test and report the totals line, then git status
> and git diff --stat. Do not commit. List every file you created or modified
> and point out anything where you had to make a judgment call.
> ```
>
> **Why the boundary sits there:** the type conditional lives only in
> `workflow/`, and everything outside it (the domain types, `NotificationHub`,
> `PriceCalculator`, `ReportService`) is a collaborator whose public surface the
> refactor has no reason to change. Keeping the boundary there also means any
> change outside `workflow/` is out of scope by definition and easy to spot.
> I kept `domain/` out on purpose. Putting behavior on the `BookingType` enum
> would also remove the switch, but it would drag notifications and the store
> into the domain layer.

### The result

**The diff and the suite.** How you are showing the diff to the TA (a commit,
`git diff`, a branch), and the totals line (the shipped count plus your pin,
all green).

> The history on `main` is in order:
> `a8dc7f0` (the pin plus this pin section), then `746c085` (the refactor
> alone), then the commit with the rest of this write-up. The diff is
> `git show 746c085`: 6 files, all in `workflow/`, +359 / -212.
> `BookingWorkflow.java` shrinks from 296 lines to a constructor that fills the
> `EnumMap` plus four short dispatch methods. It adds `BookingKind` (interface),
> `RegularBookingKind`, `RecurringBookingKind`, `BlockedBookingKind`, and
> `WorkflowContext` (the shared store, calculator, hub, `FACILITIES_CONTACT`,
> and `recipientFor`).
>
> Totals after the refactor (`mvn -B test`):
> `Tests run: 36, Failures: 0, Errors: 0, Skipped: 0` and `BUILD SUCCESS`. That
> is the shipped 35 (18 + 7 + 6 + 4) plus 1 for
> `BookingWorkflowCharacterizationTest`.

**What did NOT change: behavior and files.** The observable behavior you
checked is still the same, including anything that surprised you while reading.
Which files outside the scope are untouched, and how you verified that rather
than assumed it. If the agent reached outside the directive, say where and what
you did about it.

> **Behavior.** I did not rely on the tests alone; I also checked the code
> mechanically:
> - I extracted every string literal from the old `BookingWorkflow.java`
>   (`git show HEAD~1:...`) and from all the new `workflow/*.java` files,
>   sorted both lists, and diffed them. They are **identical**, so every
>   message, subject, and description is unchanged.
> - I extracted every `compareTo(...) <op> 0` expression from the old and new
>   code and counted them. The counts match exactly, including the single
>   `existing.getEnd()) <= 0` / `slot.end()) <= 0` pair, which now lives in
>   `RecurringBookingKind.submit`. That pair is the surprise my pin covers,
>   and the pin is still green.
> - `grep -rnE 'switch|case (REGULAR|RECURRING|BLOCKED)' workflow/` returns
>   nothing.
> - I read each branch side by side with the original. The check order is the
>   same (member, capacity, occurrence bounds; the block's same-day check comes
>   before the overlap check). `nextSeriesId()` is still consumed before the
>   loop. RECURRING still has no member double-booking check. Recurring cancel
>   still releases this occurrence *and every later one* (`< 0` skips earlier
>   ones). Recurring `priceOf` still sums the whole live series, not just the
>   one occurrence asked about. All three of those surprised me on first
>   read, and no shipped test pins the last two.
>
> **Files.** `git status --short -- . ':!src/main/java/.../workflow'` was empty
> before the commit, and `git show --stat 746c085` lists only `workflow/`
> files. Nothing under `domain/`, `notify/`, `pricing/`, `reporting/`, or
> `src/test/` changed, and neither did `pom.xml`. The agent stayed inside the
> directive.

**One thing the agent changed that you had to look at twice.** Something you
checked line by line before accepting. If there was nothing, say how carefully
you read the diff.

> `BookingWorkflow` **no longer holds `calculator` or `hub` fields**, and the
> two private constants moved: `MAX_SERIES_WEEKS` went into
> `RecurringBookingKind` and `FACILITIES_CONTACT` went into `WorkflowContext`.
> I checked that the constructor still null-checks all three collaborators
> before it builds anything, with the same exception message, because
> dropping the fields could easily have dropped that check too. It did not.
> All of these were private, so no caller can tell. I also re-read
> `RegularBookingKind.cancel` line by line, because the agent re-wrapped the
> `NotificationMessage(...)` call there. The arguments and their order
> (recipient, `"Booking cancelled"`, body, `booking.getStart()`) are unchanged.

### The closing explanation

**Refactor or regenerate?** Argue whether regenerating `BookingWorkflow` from scratch
would have been the better call, using the lecture's four questions (test
coverage, code age, spec quality, and reach). Be concrete about this codebase.

> **Refactoring was the right call. Regenerating would have been worse.**
> - **Test coverage:** this is the argument *against* regenerating. The suite
>   is green but thin on exactly the parts a regeneration would redecide.
>   Before my pin, nothing covered skipped weeks, the `<=` boundary, occurrence
>   indices, recurring cancel releasing every later occurrence, unknown members,
>   the 1..26 occurrence bounds, or a block that crosses midnight. A
>   regeneration could change any of these and still pass all 35. A refactor
>   that moves code without rewriting it keeps those behaviors by
>   construction, and the string and operator diff above confirms it.
> - **Code age:** the code is young (one initial commit) and agent-generated,
>   so no years of bug fixes are hidden in it. That is the one point in favor
>   of regenerating. But young does not mean unused. It already encodes
>   choices (series skip instead of fail, buffer around series slots) that
>   somebody may depend on.
> - **Spec quality:** poor. The only spec is the README line ("submit, cancel,
>   price, and describe a booking") plus javadoc, and the javadoc contradicts
>   the code. `TimeSlot` says the end is exclusive, but `RecurringBookingKind`
>   treats it as inclusive. Regenerating from a spec that weak means the
>   generator decides all the unwritten rules again, and it would side with
>   the javadoc.
> - **Reach:** high. The class comment says every store write and every
>   notification goes through `BookingWorkflow`. Its outputs (booking IDs, series
>   IDs, outbox text, `describe` strings) are read by `ReportServiceTest`
>   and `NotificationHubTest` as well as its own tests, and in a real
>   deployment by users' inboxes. A behavior change here shows up everywhere.
>
> Thin pins, a weak spec, and high reach all point to a structure-only
> refactor whose sameness we can check, not a rewrite whose sameness we would
> have to hope for.

**What would flip your answer.** A condition about the artifact, not a feeling.

> I would regenerate if a written spec for `BookingWorkflow` existed and the
> suite pinned its rules per booking type: the overlap boundary for each type,
> series skip and index rules, how far cancel reaches, the price scope, and
> every rejection message. Then a regeneration could be checked against the
> tests instead of trusted. I would also regenerate if the spec deliberately
> changed one of those rules (for example, "series slots use the same half-open
> overlap as everything else"), since the code would have to change anyway.

---

## Milestone 2: The pattern critique

Read `notify/`. It works and the outbox tests pass.

### The patterns present

List every design pattern you can name in that package. For each one, the class
or classes that carry it.

> 1. **Singleton:** `NotifierFactory` (private constructor, static `instance`,
>    synchronized `getInstance()`).
> 2. **Factory** (a simple factory method): `NotifierFactory.createStrategy()`
>    returns a `NotificationStrategy`.
> 3. **Strategy:** `NotificationStrategy` (interface) and
>    `EmailNotificationStrategy` (its only implementation), held by
>    `NotificationHub.strategy`.
> 4. **Observer (publish/subscribe):** `NotificationHub` is the subject
>    (`subscribe`, `publish` loops over `subscribers`), `NotificationSubscriber`
>    is the observer interface, and `OutboxSubscriber` is its only concrete
>    observer, which forwards to `Outbox`.

### The problem each one solves

For each pattern you listed, what would have to be true about the requirements
for that pattern to be the right call? One sentence each, not in terms of
"flexibility".

> - **Singleton:** there must be a resource that it would be *wrong* to have two
>   of, such as shared mutable state, a connection pool, or a config loaded
>   once, and many unrelated callers must reach it.
> - **Factory:** the concrete class to build must be chosen at run time from
>   something the caller does not know or should not care about, such as
>   config, the environment, or an input value.
> - **Strategy:** there must be two or more real ways to render a message, and
>   which one is used must vary per hub, per message, or per recipient.
> - **Observer:** a publish must reach a set of receivers that changes, that
>   the publisher must not know about, and that gets added to by code other
>   than the publisher's constructor.

### Which of those problems exist here

For each pattern, does the problem it solves exist in this codebase? Point at
the code that settles it.

> - **Singleton: no.** `NotifierFactory` has no fields besides `instance`
>   and no state, so two instances would behave identically. It is called
>   from exactly one place, `NotificationHub`'s constructor
>   (`NotificationHub.java:22`). The only thing that needs it to be a
>   singleton is the test `factoryHandsBackTheSameInstance`, which tests the
>   pattern itself, not any behavior.
> - **Factory: no.** `createStrategy()` takes no arguments, reads no config,
>   and always returns `new EmailNotificationStrategy()`
>   (`NotifierFactory.java:19-21`). It makes no choice.
> - **Strategy: no.** There is one implementation, `EmailNotificationStrategy`,
>   and it cannot even be swapped. `NotificationHub` has no constructor or
>   setter that accepts a strategy, because the field is assigned from the
>   factory inside the constructor (`NotificationHub.java:22`). Nothing in the
>   codebase varies the format. `Member` has no channel preference and
>   `NotificationMessage` has no channel field.
> - **Observer: no.** The only call to `subscribe(...)` in `src/main` is the
>   hub's own constructor subscribing its own `OutboxSubscriber`
>   (`NotificationHub.java:23`; checked with `grep -rn "subscribe(" src/main`).
>   `BookingWorkflow` only ever calls `hub.publish`. The test
>   `hubDeliversToItsOneSubscriber` asserts `subscriberCount() == 1`. The
>   publisher already knows its one receiver, because it creates it.

### The simpler structure

**Your proposal.** What replaces `notify/`. Sketch the classes and the one
method that matters.

> Two classes plus the message record:
>
> ```java
> public record NotificationMessage(...)          // unchanged
>
> public class Outbox { ... }                      // unchanged
>
> public class NotificationHub {
>     private final Outbox outbox;
>     public NotificationHub() { this(new Outbox()); }
>     public NotificationHub(Outbox outbox) { /* null check */ this.outbox = outbox; }
>
>     public void publish(NotificationMessage m) {
>         outbox.append("To: " + m.recipient() + " | Subject: " + m.subject()
>                 + " | " + m.body());
>     }
>     public Outbox getOutbox() { return outbox; }
> }
> ```
>
> This deletes `NotifierFactory`, `NotificationStrategy`,
> `EmailNotificationStrategy`, `NotificationSubscriber`, and `OutboxSubscriber`.
> I kept the name `NotificationHub` so `BookingWorkflow`'s constructor and
> its `hub.publish(...)` call sites do not change.

**What stays the same.** The tested behavior it must still produce, named
precisely enough that a reader can check it against the shipped tests.

> - Each `publish` appends exactly one string to the outbox, in call order,
>   formatted `To: <recipient> | Subject: <subject> | <body>`
>   (`publishedMessageLandsInTheOutboxFullyRendered`,
>   `aConfirmationFromTheWorkflowReachesTheOutbox`).
> - `getOutbox()` returns that `Outbox`, and `size()` / `last()` work as
>   before. Every outbox-count assertion in `BookingWorkflowTest` depends on
>   this (for example, 4 messages for a 4-week series, 2 after a regular
>   submit and cancel, and 0 after a rejected submit).
> - `NotificationMessage` still rejects a blank recipient or a null subject.
> - `new NotificationHub(null)` still throws `IllegalArgumentException`.
>
> Two shipped tests pin the *structure*, not the behavior:
> `factoryHandsBackTheSameInstance` and `hubDeliversToItsOneSubscriber`. The
> second could survive if `subscriberCount()` returned 1, but that would be a
> stub lying to the test. The first cannot survive without the factory. Under
> the lab's rules, which forbid editing or deleting a shipped test, this
> stays a proposal. Carrying it out would mean retiring those two tests on
> purpose, as tests of design choices rather than requirements.

**What you would keep, if anything.** If you would keep one interface, say
which and why. "None of it" is a fine answer if you can defend it.

> **None of the interfaces.** `NotificationMessage` and `Outbox` stay, but
> they are plain data, not pattern machinery. Each interface has exactly
> one implementation and no second one on the horizon. Extracting an
> interface later, when a second implementation shows up, is a mechanical
> refactor that IDEs automate. Keeping it now costs a layer of indirection on
> every read.

### What would bring each layer back

For at least two of the layers you would remove, what requirement, if it
arrived next sprint, would make that layer the right structure? Be specific
about the requirement, not about the pattern.

> - **`NotificationStrategy` (Strategy):** "Members can choose to get their
>   confirmations by SMS instead of email, and an SMS must fit in 160
>   characters with no `Subject:` header." Rendering would then really depend
>   on a per-member setting (a new `Member.channel`), and there would be two
>   formats chosen at run time.
> - **`NotificationSubscriber` / `OutboxSubscriber` (Observer):** "Every
>   booking event must also go to the audit log and to the facilities Slack
>   channel, and the operations team will add more destinations by
>   configuration without touching `BookingWorkflow`." That gives several
>   receivers that change over time and that the publisher should not know
>   about.
> - **`NotifierFactory` (Factory + Singleton):** "The notification format is
>   chosen per deployment from `application.properties` (email in production,
>   a plain-text format in staging), and the file must be read once at
>   startup." Then there is a real run-time choice to make and a config that
>   should be loaded once. Even then I would inject the result into the hub
>   rather than call a global `getInstance()`.

**Misuse or anti-pattern?** Say which this is and why the distinction matters.

> **Mostly misuse.** Strategy, Factory, and Observer are good patterns,
> applied here to problems this codebase does not have (speculative
> generality). They would be the right structure once one of the
> requirements above arrives. The **Singleton** is closer to an
> anti-pattern. A global access point inside `NotificationHub`'s constructor
> hides a dependency and makes the strategy impossible to inject in a test,
> and that harm exists no matter what the requirements are. The distinction
> matters for what you do next. A misuse is fixed by deleting the layer and
> remembering the requirement that would justify adding it back. An
> anti-pattern should be replaced, with the dependency passed in, even when
> the requirement for it does arrive.

---

## Milestone 3: The missing pattern

Read `pricing/`. Not coded, one sentence.

**The pattern.** Which one fits `PriceCalculator`, and the problem that makes
it fit. Name the problem.

> **Strategy, applied per rule as an ordered list of `PricingRule` objects**
> (each one takes a running price and the booking and returns an adjusted
> price) fits `PriceCalculator.price`. The problem it fits: the class javadoc
> describes a *published, ordered set of independent pricing rules* (base
> rate, weekend surcharge, long-booking discount, tier discount, "in the
> order they apply"). Today that order exists only as the line order of one
> method, so adding, removing, or reordering a rule (a holiday surcharge, a
> promo code, or the tier discount applied before the long-booking discount)
> means editing the inside of `price` instead of editing the list of rules.

**Would you apply it today?** Yes or no, one line, with the reason.

> **No.** There are four stable rules in about 20 lines, all pinned by
> `PriceCalculatorTest` (including `everyRuleAppliesInOrder`), and no
> requirement yet asks for a fifth rule or a different set of rules per room
> or member. I would apply it when that requirement arrives.
