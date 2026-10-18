package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
)

func TestSimplifyFalseAnd(t *testing.T) {
	rule := loadRule(t, "SimplifyFalseAnd")
	e := newEnv(t)
	input, cols := e.scan("t", 3)
	right := e.f.ConstructEq(e.v(cols[1]), e.num(7))
	before := e.f.ConstructSelect(input, e.filters(e.f.ConstructAnd(memo.FalseSingleton, right)))
	want := e.f.ConstructSelect(input, e.filters(memo.FalseSingleton))
	e.expectRewrite(rule, before, want)

	nearMiss, nearMissCols := e.scan("u", 2)
	nearMissRight := e.f.ConstructEq(e.v(nearMissCols[1]), e.num(7))
	e.expectNoMatch(rule, e.f.ConstructSelect(nearMiss, e.filters(e.f.ConstructOr(memo.FalseSingleton, nearMissRight))))
}
