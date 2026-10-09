package edu.cmu.cs214.scheduling.workflow;

import edu.cmu.cs214.scheduling.domain.Booking;
import edu.cmu.cs214.scheduling.domain.BookingOutcome;
import edu.cmu.cs214.scheduling.domain.BookingRequest;
import edu.cmu.cs214.scheduling.domain.BookingStore;
import edu.cmu.cs214.scheduling.domain.Member;
import edu.cmu.cs214.scheduling.domain.MembershipTier;
import edu.cmu.cs214.scheduling.domain.Room;
import edu.cmu.cs214.scheduling.domain.TimeSlot;
import edu.cmu.cs214.scheduling.notify.NotificationHub;
import edu.cmu.cs214.scheduling.pricing.PriceCalculator;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Characterization tests: they pin what the shipped workflow does today, not
 * what it should do. Written before the refactor.
 */
class BookingWorkflowCharacterizationTest {

    @Test
    void recurringSubmitSkipsAWeekWhoseSlotOnlyTouchesAnExistingBooking() {
        BookingStore store = new BookingStore();
        store.addRoom(new Room("C-200", "Cedar Hall", 20));
        store.addMember(new Member("m-1", "Ada", "ada@rooms.example.edu", MembershipTier.BASIC));
        store.addMember(new Member("m-2", "Grace", "grace@rooms.example.edu",
                MembershipTier.PREMIER));
        NotificationHub hub = new NotificationHub();
        BookingWorkflow workflow = new BookingWorkflow(store, new PriceCalculator(), hub);

        // Week 2 of the series: a regular booking that starts exactly when the series slot ends.
        workflow.submit(BookingRequest.regular("C-200", "m-2",
                LocalDateTime.of(2026, 10, 12, 10, 0), LocalDateTime.of(2026, 10, 12, 11, 0), 4));

        BookingOutcome outcome = workflow.submit(BookingRequest.recurring("C-200", "m-1",
                LocalDateTime.of(2026, 10, 5, 9, 0), LocalDateTime.of(2026, 10, 5, 10, 0), 3, 6));

        // Touching counts as a conflict for a series (a regular booking would be accepted).
        assertTrue(outcome.isAccepted());
        assertEquals(List.of(new TimeSlot(LocalDateTime.of(2026, 10, 12, 9, 0),
                LocalDateTime.of(2026, 10, 12, 10, 0))), outcome.getSkipped());
        assertEquals("series S-1: 2 booked, 1 skipped", outcome.getMessage());

        // The skipped week leaves a gap in the occurrence index, not a renumbering.
        List<Booking> booked = outcome.getBooked();
        assertEquals(2, booked.size());
        assertEquals(1, booked.get(0).getOccurrenceIndex());
        assertEquals(3, booked.get(1).getOccurrenceIndex());
        assertEquals(LocalDateTime.of(2026, 10, 19, 9, 0), booked.get(1).getStart());

        // One confirmation for the regular booking, one per written occurrence.
        assertEquals(3, hub.getOutbox().size());
    }
}
