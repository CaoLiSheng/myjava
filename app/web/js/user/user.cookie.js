/**
 * Created by cls on 15-3-26.
 */

function getUserName() {
    var username = $.cookie("un");
    if ( username ) {
        console.info("username = " + username);
        return username;
    }
    return null;
}