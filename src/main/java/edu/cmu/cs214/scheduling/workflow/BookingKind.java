package edu.cmu.cs214.scheduling.workflow;

import edu.cmu.cs214.scheduling.domain.Booking;
import edu.cmu.cs214.scheduling.domain.BookingOutcome;
import edu.cmu.cs214.scheduling.domain.BookingRequest;
import edu.cmu.cs214.scheduling.domain.Room;

/**
 * The type-specific half of the workflow. {@link BookingWorkflow} does the
 * shared lookups and hands each call to the kind registered for the booking's
 * type.
 */
interface BookingKind {

    /** Validates and writes a request whose room is already known to exist. */
    BookingOutcome submit(BookingRequest request, Room room);

    /** Releases a live booking; {@code roomName} is the room's name or, failing that, its id. */
    boolean cancel(Booking booking, String roomName, boolean adminOverride);

    /** What the holder owes for a booking, in dollars. */
    double price(Booking booking);

    /** A one-line summary; {@code roomName} is the room's name or, failing that, its id. */
    String describe(Booking booking, String roomName);
}
