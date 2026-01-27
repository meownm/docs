package com.demo.passport;

class NfcDispatchTransition {
    enum Action {
        ENABLE,
        DISABLE,
        NONE
    }

    static Action from(MainActivity.State previousState, MainActivity.State nextState) {
        if (previousState != MainActivity.State.NFC_WAIT && nextState == MainActivity.State.NFC_WAIT) {
            return Action.ENABLE;
        }
        if (previousState == MainActivity.State.NFC_WAIT && nextState != MainActivity.State.NFC_WAIT) {
            return Action.DISABLE;
        }
        return Action.NONE;
    }
}
