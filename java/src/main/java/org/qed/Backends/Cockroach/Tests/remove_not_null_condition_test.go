package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
)

func TestRemoveNotNullCondition(t *testing.T) {
	rule := loadRule(t, "RemoveNotNullCondition")
	e := newEnv(t)

	input, cols := e.scan("t", 2)
	other := e.f.ConstructEq(e.v(cols[1]), e.num(7))
	target := e.f.ConstructIsNot(e.v(cols[0]), memo.NullSingleton)
	before := e.f.ConstructSelect(e.f.ConstructSelect(input, e.filters(other)), e.filters(target))
	want := e.f.ConstructSelect(input, e.filters(other))
	e.expectRewrite(rule, before, want)

	input, cols = e.scan("t", 2)
	other = e.f.ConstructEq(e.v(cols[0]), e.num(7))
	target = e.f.ConstructIsNot(e.v(cols[1]), memo.NullSingleton)
	nearMiss := e.f.ConstructSelect(e.f.ConstructSelect(input, e.filters(other)), e.filters(target))
	e.expectNoMatch(rule, nearMiss)
}
