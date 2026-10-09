package edu.cmu.cs214.scheduling.workflow;

import edu.cmu.cs214.scheduling.domain.Booking;
import edu.cmu.cs214.scheduling.domain.BookingOutcome;
import edu.cmu.cs214.scheduling.domain.BookingRequest;
import edu.cmu.cs214.scheduling.domain.BookingStore;
import edu.cmu.cs214.scheduling.domain.BookingType;
import edu.cmu.cs214.scheduling.domain.Member;
import edu.cmu.cs214.scheduling.domain.Room;
import edu.cmu.cs214.scheduling.domain.TimeSlot;
import edu.cmu.cs214.scheduling.notify.NotificationHub;
import edu.cmu.cs214.scheduling.notify.NotificationMessage;
import edu.cmu.cs214.scheduling.pricing.PriceCalculator;

import java.util.ArrayList;
import java.util.List;

/** A weekly series held by one member. */
final class RecurringBookingKind implements BookingKind {

    private static final int MAX_SERIES_WEEKS = 26;

    private final BookingStore store;
    private final PriceCalculator calculator;
    private final NotificationHub hub;

    RecurringBookingKind(WorkflowContext context) {
        this.store = context.store;
        this.calculator = context.calculator;
        this.hub = context.hub;
    }

    @Override
    public BookingOutcome submit(BookingRequest request, Room room) {
        Member member = store.findMember(request.memberId());
        if (member == null) {
            return BookingOutcome.rejected("unknown member " + request.memberId());
        }
        if (request.attendees() > room.getCapacity()) {
            return BookingOutcome.rejected("room " + room.getId() + " seats "
                    + room.getCapacity() + ", request wants " + request.attendees());
        }
        if (request.occurrences() < 1) {
            return BookingOutcome.rejected("a series needs at least one occurrence");
        }
        if (request.occurrences() > MAX_SERIES_WEEKS) {
            return BookingOutcome.rejected("a series runs at most "
                    + MAX_SERIES_WEEKS + " weeks");
        }

        String seriesId = store.nextSeriesId();
        List<Booking> booked = new ArrayList<>();
        List<TimeSlot> skipped = new ArrayList<>();

        for (int week = 0; week < request.occurrences(); week++) {
            TimeSlot slot = request.slot().plusWeeks(week);
            boolean taken = false;
            for (Booking existing : store.activeInRoom(room.getId())) {
                if (existing.getStart().compareTo(slot.end()) <= 0
                        && slot.start().compareTo(existing.getEnd()) <= 0) {
                    taken = true;
                    break;
                }
            }
            if (taken) {
                skipped.add(slot);
                continue;
            }

            Booking occurrence = new Booking(store.nextBookingId(), room.getId(),
                    member.getId(), slot, BookingType.RECURRING, seriesId, week + 1);
            store.save(occurrence);
            booked.add(occurrence);
            hub.publish(new NotificationMessage(member.getEmail(), "Occurrence confirmed",
                    "Room " + room.getName() + " on " + slot.start().toLocalDate()
                            + " in series " + seriesId, slot.start()));
        }

        return BookingOutcome.series(booked, skipped, "series " + seriesId + ": "
                + booked.size() + " booked, " + skipped.size() + " skipped");
    }

    @Override
    public boolean cancel(Booking booking, String roomName, boolean adminOverride) {
        Member member = store.findMember(booking.getMemberId());
        for (Booking occurrence : store.seriesOccurrences(booking.getSeriesId())) {
            if (occurrence.isCancelled()
                    || occurrence.getStart().compareTo(booking.getStart()) < 0) {
                continue;
            }
            occurrence.cancel();
            hub.publish(new NotificationMessage(WorkflowContext.recipientFor(member),
                    "Occurrence cancelled",
                    "Room " + roomName + " on " + occurrence.getStart().toLocalDate()
                            + " in series " + occurrence.getSeriesId() + " is free again",
                    occurrence.getStart()));
        }
        return true;
    }

    @Override
    public double price(Booking booking) {
        Member member = store.findMember(booking.getMemberId());
        double total = 0.0;
        for (Booking occurrence : store.seriesOccurrences(booking.getSeriesId())) {
            if (occurrence.isCancelled()) {
                continue;
            }
            total += calculator.price(occurrence, member);
        }
        return total;
    }

    @Override
    public String describe(Booking booking, String roomName) {
        return "Recurring booking #" + booking.getId() + " in " + roomName
                + ", occurrence " + booking.getOccurrenceIndex() + " of series "
                + booking.getSeriesId() + ", " + booking.getStart()
                + " to " + booking.getEnd();
    }
}
