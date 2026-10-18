package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt"
	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
	"github.com/cockroachdb/cockroach/pkg/sql/types"
)

func TestConvertToCatalyst(t *testing.T) {
	rule := loadRule(t, "ConvertToCatalyst")
	e := newEnv(t)

	s, sc := e.scan("t", 1)
	x := sc[0]
	tcol := e.f.Metadata().AddColumn("shared", types.Int)
	ex := e.f.ConstructPlus(e.v(x), e.num(1))
	before := e.f.ConstructSelect(s, e.filters(e.f.ConstructEq(ex, ex)))
	inner := e.f.ConstructProject(s,
		memo.ProjectionsExpr{e.f.ConstructProjectionsItem(e.f.ConstructPlus(e.v(x), e.num(1)), tcol)},
		opt.MakeColSet(x))
	want := e.f.ConstructProject(
		e.f.ConstructSelect(inner, e.filters(e.f.ConstructEq(e.v(tcol), e.v(tcol)))),
		memo.ProjectionsExpr{}, opt.MakeColSet(x))
	e.expectRewriteUpToFreshColumns(rule, before, want)

	s2, sc2 := e.scan("t", 2)
	x2, y := sc2[0], sc2[1]
	ex2 := e.f.ConstructPlus(e.v(x2), e.num(1))
	ey := e.f.ConstructPlus(e.v(y), e.num(1))
	e.expectNoMatch(rule, e.f.ConstructSelect(s2, e.filters(e.f.ConstructEq(ex2, ey))))
	e.expectNoMatch(rule, e.f.ConstructSelect(s, e.filters(e.f.ConstructUnaryMinus(ex))))
	e.expectNoMatch(rule, e.f.ConstructSelect(s, e.filters(e.f.ConstructEq(ex, ex), e.f.ConstructEq(ex, ex))))
}
