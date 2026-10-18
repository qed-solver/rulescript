package rulescript_test

import "testing"

func TestNormalizeCmpPlusConst(t *testing.T) {
	rule := loadRule(t, "NormalizeCmpPlusConst")
	t.Run("rewrite_less_than", func(t *testing.T) {
		e := newEnv(t)
		scan, cols := e.scan("t", 3)
		before := e.f.ConstructSelect(scan, e.filters(
			e.f.ConstructLt(
				e.f.ConstructPlus(e.v(cols[0]), e.v(cols[1])),
				e.v(cols[2]),
			),
		))
		want := e.f.ConstructSelect(scan, e.filters(
			e.f.ConstructLt(
				e.v(cols[0]),
				e.f.ConstructMinus(e.v(cols[2]), e.v(cols[1])),
			),
		))
		t.Logf("normalized input: %s", e.format(before))
		e.expectRewrite(rule, before, want)
	})

	t.Run("does_not_move_right_side_addition", func(t *testing.T) {
		e := newEnv(t)
		scan, cols := e.scan("t", 3)
		plan := e.f.ConstructSelect(scan, e.filters(
			e.f.ConstructLt(
				e.v(cols[0]),
				e.f.ConstructPlus(e.v(cols[1]), e.v(cols[2])),
			),
		))
		e.expectNoMatch(rule, plan)
	})
}
