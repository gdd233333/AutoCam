#include "autocam/add.h"

#include <gtest/gtest.h>

TEST(Add, Smoke) {
    EXPECT_EQ(autocam::add(2, 3), 5);
    EXPECT_EQ(autocam::add(-1, 1), 0);
    EXPECT_EQ(autocam::add(0, 0), 0);
}
