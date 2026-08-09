import pytest
from engine.market import generate_offers, OfferCard, update_player_value
from engine.world import build_world

WORLD = build_world("data")


def test_generate_offers_based_on_value():
    player = {"name": "张伟", "ovr": 74, "age": 19, "position": "ST",
              "value": 8_000_000, "club": "山东泰山", "league": "cs"}
    cards = generate_offers(player, world=WORLD, window="summer", rng_seed=5)
    assert 1 <= len(cards) <= 3
    for c in cards:
        if c["fee"] > 0:  # 租借报价 fee 为 0
            assert c["fee"] >= 0.8 * player["value"]
            assert c["fee"] <= 1.5 * player["value"] * 1.5  # 溢价上限


def test_offer_card_fields():
    c = OfferCard(club="上海海港", league="cs", fee=10_000_000, weekly_wage=50_000,
                  years=4, release_clause=30_000_000, signing_bonus=1_000_000, role="主力")
    assert c["role"] == "主力"
    assert c["release_clause"] > c["fee"]


def test_update_player_value_changes_with_age_ovr():
    v1 = update_player_value(ovr=76, age=20, position="ST", league="cs", contract_years=2, form=1.0)
    v2 = update_player_value(ovr=76, age=30, position="ST", league="cs", contract_years=2, form=1.0)
    assert v1 > v2


def test_young_low_ovr_can_get_loan_offers():
    """16-17 岁低实力球员也可能收到租借培养报价（v2 放宽门槛）。"""
    player = {"name": "青训生", "ovr": 63, "age": 17, "position": "CM",
              "value": 400_000, "club": "鲁能U17", "league": "cs"}
    found_offer = False
    for seed in range(20):
        cards = generate_offers(player, world=WORLD, rng_seed=seed)
        if cards:
            found_offer = True
            assert cards[0]["club"]  # 真实球队名
            assert cards[0]["league"] in WORLD
            break
    assert found_offer, "17 岁 63 OVR 球员 20 次内应至少收到一次报价"


def test_star_gets_3_to_4_offers():
    player = {"name": "巨星", "ovr": 86, "age": 24, "position": "ST",
              "value": 80_000_000, "club": "曼城", "league": "epl"}
    for seed in range(10):
        cards = generate_offers(player, world=WORLD, rng_seed=seed)
        assert 3 <= len(cards) <= 4


def test_buyers_from_world_pool_not_own_club():
    player = {"name": "张伟", "ovr": 78, "age": 20, "position": "ST",
              "value": 15_000_000, "club": "山东泰山", "league": "cs", "team_id": "cs02"}
    cards = generate_offers(player, world=WORLD, rng_seed=3)
    for c in cards:
        assert c["club"] != "山东泰山"  # 不会自买自卖
