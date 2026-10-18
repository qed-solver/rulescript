package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
	"github.com/cockroachdb/cockroach/pkg/sql/types"
)

func TestNullDownPropagation(t *testing.T) {
	e := newEnv(t)
	rule := loadRule(t, "NullDownPropagation")
	e.f.AddExternalRules(nil)
	input, cols := e.scan("t", 2)
	x, y := e.v(cols[0]), e.v(cols[1])
	comparison := e.f.ConstructLt(x, y)
	boolNull := e.f.ConstructNull(types.Bool)
	isNull := e.f.ConstructIs(comparison, boolNull)
	before := e.f.ConstructSelect(input, e.filters(isNull))
	want := e.f.ConstructSelect(input, e.filters(e.f.ConstructOr(
		e.f.ConstructIs(x, memo.NullSingleton),
		e.f.ConstructIs(y, memo.NullSingleton),
	)))
	e.expectRewrite(rule, before, want)

	nearMiss, nearMissCols := e.scan("t", 2)
	left, right := e.v(nearMissCols[0]), e.v(nearMissCols[1])
	e.expectNoMatch(rule, e.f.ConstructSelect(nearMiss, e.filters(e.f.ConstructIsNot(
		e.f.ConstructLt(left, right), e.f.ConstructNull(types.Bool),
	))))
}
