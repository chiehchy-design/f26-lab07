package edu.cmu.cs214.scheduling.workflow;

import edu.cmu.cs214.scheduling.domain.BookingStore;
import edu.cmu.cs214.scheduling.domain.Member;
import edu.cmu.cs214.scheduling.notify.NotificationHub;
import edu.cmu.cs214.scheduling.pricing.PriceCalculator;

/** The collaborators and constants every booking kind shares. */
final class WorkflowContext {

    static final String FACILITIES_CONTACT = "facilities@rooms.example.edu";

    final BookingStore store;
    final PriceCalculator calculator;
    final NotificationHub hub;

    WorkflowContext(BookingStore store, PriceCalculator calculator, NotificationHub hub) {
        this.store = store;
        this.calculator = calculator;
        this.hub = hub;
    }

    static String recipientFor(Member member) {
        return member == null ? FACILITIES_CONTACT : member.getEmail();
    }
}
