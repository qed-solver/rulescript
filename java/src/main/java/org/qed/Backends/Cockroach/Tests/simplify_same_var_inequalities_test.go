package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
	"github.com/cockroachdb/cockroach/pkg/sql/types"
)

func TestSimplifySameVarInequalities(t *testing.T) {
	rule := loadRule(t, "SimplifySameVarInequalities")
	e := newEnv(t)
	input, cols := e.scan("t", 3)
	x := e.v(cols[0])
	before := e.f.ConstructSelect(input, e.filters(e.f.ConstructNe(x, x)))
	want := e.f.ConstructSelect(input, e.filters(e.f.ConstructIs(x, memo.NullSingleton), e.f.ConstructNull(types.Bool)))
	e.expectRewrite(rule, before, want)

	miss := e.f.ConstructSelect(input, e.filters(e.f.ConstructNe(e.v(cols[0]), e.v(cols[1]))))
	e.expectNoMatch(rule, miss)
	miss = e.f.ConstructSelect(input, e.filters(e.f.ConstructEq(e.v(cols[0]), e.v(cols[0]))))
	e.expectNoMatch(rule, miss)
}
