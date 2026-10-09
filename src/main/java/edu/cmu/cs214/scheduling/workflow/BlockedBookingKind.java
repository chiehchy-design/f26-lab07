package edu.cmu.cs214.scheduling.workflow;

import edu.cmu.cs214.scheduling.domain.Booking;
import edu.cmu.cs214.scheduling.domain.BookingOutcome;
import edu.cmu.cs214.scheduling.domain.BookingRequest;
import edu.cmu.cs214.scheduling.domain.BookingStore;
import edu.cmu.cs214.scheduling.domain.BookingType;
import edu.cmu.cs214.scheduling.domain.Room;
import edu.cmu.cs214.scheduling.domain.TimeSlot;
import edu.cmu.cs214.scheduling.notify.NotificationHub;
import edu.cmu.cs214.scheduling.notify.NotificationMessage;

/** An administrative hold. No member, no charge. */
final class BlockedBookingKind implements BookingKind {

    private final BookingStore store;
    private final NotificationHub hub;

    BlockedBookingKind(WorkflowContext context) {
        this.store = context.store;
        this.hub = context.hub;
    }

    @Override
    public BookingOutcome submit(BookingRequest request, Room room) {
        TimeSlot slot = request.slot();
        if (!slot.start().toLocalDate().equals(slot.end().toLocalDate())) {
            return BookingOutcome.rejected("a block must stay inside one day");
        }

        for (Booking existing : store.activeInRoom(room.getId())) {
            if (existing.getStart().compareTo(slot.end()) < 0
                    && slot.start().compareTo(existing.getEnd()) < 0) {
                return BookingOutcome.rejected("room " + room.getId()
                        + " cannot be blocked at " + slot.start());
            }
        }

        Booking block = new Booking(store.nextBookingId(), room.getId(), null, slot,
                BookingType.BLOCKED, null, 0);
        store.save(block);
        hub.publish(new NotificationMessage(WorkflowContext.FACILITIES_CONTACT, "Room blocked",
                "Room " + room.getName() + " held from " + slot.start()
                        + " to " + slot.end(), slot.start()));
        return BookingOutcome.confirmed(block, "blocked " + room.getId());
    }

    @Override
    public boolean cancel(Booking booking, String roomName, boolean adminOverride) {
        if (!adminOverride) {
            return false;
        }
        booking.cancel();
        hub.publish(new NotificationMessage(WorkflowContext.FACILITIES_CONTACT, "Block released",
                "Room " + roomName + " released from " + booking.getStart()
                        + " to " + booking.getEnd(), booking.getStart()));
        return true;
    }

    @Override
    public double price(Booking booking) {
        return 0.0;
    }

    @Override
    public String describe(Booking booking, String roomName) {
        return "Blocked slot #" + booking.getId() + " in " + roomName
                + " from " + booking.getStart() + " to " + booking.getEnd()
                + ", admin hold";
    }
}
