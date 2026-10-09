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

/** A single reservation held by one member. */
final class RegularBookingKind implements BookingKind {

    private final BookingStore store;
    private final PriceCalculator calculator;
    private final NotificationHub hub;

    RegularBookingKind(WorkflowContext context) {
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

        TimeSlot slot = request.slot();
        for (Booking existing : store.activeInRoom(room.getId())) {
            if (existing.getStart().compareTo(slot.end()) < 0
                    && slot.start().compareTo(existing.getEnd()) < 0) {
                return BookingOutcome.rejected("room " + room.getId()
                        + " is already booked at " + slot.start());
            }
        }

        for (Booking held : store.allBookings()) {
            if (held.isCancelled() || !member.getId().equals(held.getMemberId())) {
                continue;
            }
            if (held.getStart().compareTo(slot.end()) < 0
                    && slot.start().compareTo(held.getEnd()) < 0) {
                return BookingOutcome.rejected(member.getId()
                        + " already holds a booking at " + slot.start());
            }
        }

        Booking booking = new Booking(store.nextBookingId(), room.getId(), member.getId(),
                slot, BookingType.REGULAR, null, 0);
        store.save(booking);
        hub.publish(new NotificationMessage(member.getEmail(), "Booking confirmed",
                "Room " + room.getName() + " from " + slot.start() + " to " + slot.end(),
                slot.start()));
        return BookingOutcome.confirmed(booking,
                "booked " + room.getId() + " for " + member.getId());
    }

    @Override
    public boolean cancel(Booking booking, String roomName, boolean adminOverride) {
        Member member = store.findMember(booking.getMemberId());
        booking.cancel();
        hub.publish(new NotificationMessage(WorkflowContext.recipientFor(member),
                "Booking cancelled",
                "Room " + roomName + " on " + booking.getStart() + " is free again",
                booking.getStart()));
        return true;
    }

    @Override
    public double price(Booking booking) {
        Member member = store.findMember(booking.getMemberId());
        return calculator.price(booking, member);
    }

    @Override
    public String describe(Booking booking, String roomName) {
        return "Regular booking #" + booking.getId() + " in " + roomName
                + " from " + booking.getStart() + " to " + booking.getEnd();
    }
}
