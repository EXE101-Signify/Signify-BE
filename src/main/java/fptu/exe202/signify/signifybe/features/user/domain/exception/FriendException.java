package fptu.exe202.signify.signifybe.features.user.domain.exception;

import fptu.exe202.signify.apiresponse.exception.BaseException;
import org.springframework.http.HttpStatus;

public final class FriendException extends BaseException {
    private FriendException(HttpStatus status, String message) {
        super(status, message);
    }

    public static FriendException selfRequest() {
        return new FriendException(HttpStatus.BAD_REQUEST, "You cannot send a friend request to yourself");
    }

    public static FriendException userNotFound() {
        return new FriendException(HttpStatus.NOT_FOUND, "User not found");
    }

    public static FriendException requestNotFound() {
        return new FriendException(HttpStatus.NOT_FOUND, "Friend request not found");
    }

    public static FriendException friendshipNotFound() {
        return new FriendException(HttpStatus.NOT_FOUND, "Friendship not found");
    }

    public static FriendException alreadyFriends() {
        return new FriendException(HttpStatus.CONFLICT, "Users are already friends");
    }
}
