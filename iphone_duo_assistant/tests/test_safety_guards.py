import pytest

from duo_assistant.automation.mock_flow import ForbiddenActionError, assert_click_allowed


@pytest.mark.parametrize("text", ["立即付款", "去支付", "Pay Now", "确认付款（模拟）", "Apple Pay"])
def test_pay_buttons_are_forbidden(text):
    with pytest.raises(ForbiddenActionError):
        assert_click_allowed(text)


@pytest.mark.parametrize("text", ["添加到购物袋", "结账", "提交订单"])
def test_normal_buttons_allowed(text):
    assert_click_allowed(text)
