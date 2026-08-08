import pytest
from engine.market import generate_offers, OfferCard, update_player_value


def test_generate_offers_based_on_value():
    player = {"name": "张伟", "ovr": 74, "age": 19, "position": "ST",
              "value": 8_000_000, "club": "山东泰山", "league": "cs"}
    cards = generate_offers(player, window="summer", rng_seed=5)
    assert 1 <= len(cards) <= 3
    for c in cards:
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


def test_no_offers_below_68():
    player = {"name": "边缘人", "ovr": 66, "age": 25, "position": "CB",
              "value": 500_000, "club": "某队", "league": "csl2"}
    assert generate_offers(player, rng_seed=1) == []


def test_star_gets_2_to_3_offers():
    player = {"name": "巨星", "ovr": 86, "age": 24, "position": "ST",
              "value": 80_000_000, "club": "曼城", "league": "epl"}
    for seed in range(10):
        cards = generate_offers(player, rng_seed=seed)
        assert 2 <= len(cards) <= 3
