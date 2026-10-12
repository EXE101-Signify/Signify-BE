package fptu.exe202.signify.signifybe.features.user.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FriendRelationshipTest {
    @Test
    void normalizesPairAndTransitionsToAccepted() {
        FriendRelationship relationship = new FriendRelationship(9, 3, 9, 100);

        assertEquals(3, relationship.getUserLowId());
        assertEquals(9, relationship.getUserHighId());
        assertEquals(3, relationship.otherUserId(9));
        assertTrue(relationship.isPending());

        relationship.accept(200);

        assertFalse(relationship.isPending());
        assertTrue(relationship.isAccepted());
        assertEquals(200, relationship.getAcceptedAt());
        assertEquals(200, relationship.getUpdatedAt());
    }

    @Test
    void rejectsSelfRelationshipAndUnrelatedRequester() {
        assertThrows(IllegalArgumentException.class, () -> new FriendRelationship(3, 3, 3, 100));
        assertThrows(IllegalArgumentException.class, () -> new FriendRelationship(3, 9, 7, 100));
    }
}
