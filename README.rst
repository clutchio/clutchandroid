Clutch Android Client
=====================

This is the Android client library for Clutch.io.


Example
=======

Here's how you might use it for simple A/B testing:

.. sourcecode:: java

    ClutchAB.test("signUpBtnColor", new ClutchABTest() {
        public void A() {
            // Display green sign-up button
        }
        public void B() {
            // Display blue sign-up button
        }
    });


Documentation
=============

More documentation can be found at: http://docs.clutch.io/