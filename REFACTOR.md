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

### The result

**The diff and the suite.** How you are showing the diff to the TA (a commit,
`git diff`, a branch), and the totals line (the shipped count plus your pin,
all green).

**What did NOT change: behavior and files.** The observable behavior you
checked is still the same, including anything that surprised you while reading.
Which files outside the scope are untouched, and how you verified that rather
than assumed it. If the agent reached outside the directive, say where and what
you did about it.

**One thing the agent changed that you had to look at twice.** Something you
checked line by line before accepting. If there was nothing, say how carefully
you read the diff.

### The closing explanation

**Refactor or regenerate?** Argue whether regenerating `BookingWorkflow` from scratch
would have been the better call, using the lecture's four questions (test
coverage, code age, spec quality, and reach). Be concrete about this codebase.

**What would flip your answer.** A condition about the artifact, not a feeling.

---

## Milestone 2: The pattern critique

Read `notify/`. It works and the outbox tests pass.

### The patterns present

List every design pattern you can name in that package. For each one, the class
or classes that carry it.

### The problem each one solves

For each pattern you listed, what would have to be true about the requirements
for that pattern to be the right call? One sentence each, not in terms of
"flexibility".

### Which of those problems exist here

For each pattern, does the problem it solves exist in this codebase? Point at
the code that settles it.

### The simpler structure

**Your proposal.** What replaces `notify/`. Sketch the classes and the one
method that matters.

**What stays the same.** The tested behavior it must still produce, named
precisely enough that a reader can check it against the shipped tests.

**What you would keep, if anything.** If you would keep one interface, say
which and why. "None of it" is a fine answer if you can defend it.

### What would bring each layer back

For at least two of the layers you would remove, what requirement, if it
arrived next sprint, would make that layer the right structure? Be specific
about the requirement, not about the pattern.

**Misuse or anti-pattern?** Say which this is and why the distinction matters.

---

## Milestone 3: The missing pattern

Read `pricing/`. Not coded, one sentence.

**The pattern.** Which one fits `PriceCalculator`, and the problem that makes
it fit. Name the problem.

**Would you apply it today?** Yes or no, one line, with the reason.
