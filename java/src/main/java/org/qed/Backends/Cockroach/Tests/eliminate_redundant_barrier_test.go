package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt"
	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
	"github.com/cockroachdb/cockroach/pkg/sql/types"
)

func TestEliminateRedundantBarrier(t *testing.T) {
	rule := loadRule(t, "EliminateRedundantBarrier")

	e := newEnv(t)
	s, cols := e.scan("t", 3)
	inner := e.f.ConstructProject(s, memo.ProjectionsExpr{}, opt.MakeColSet(cols[0]))
	before := e.f.ConstructProject(inner, memo.ProjectionsExpr{}, opt.MakeColSet(cols[0]))
	want := e.f.ConstructProject(s, memo.ProjectionsExpr{}, opt.MakeColSet(cols[0]))
	e.expectRewrite(rule, before, want)

	e = newEnv(t)
	s, cols = e.scan("t", 3)
	wide := e.f.ConstructProject(s, memo.ProjectionsExpr{}, opt.MakeColSet(cols[0], cols[1]))
	e.expectNoMatch(rule, e.f.ConstructProject(wide, memo.ProjectionsExpr{}, opt.MakeColSet(cols[0], cols[1])))

	e = newEnv(t)
	s, cols = e.scan("t", 3)
	x := e.f.Metadata().AddColumn("x", types.Int)
	computed := e.f.ConstructProject(s, memo.ProjectionsExpr{
		e.f.ConstructProjectionsItem(e.f.ConstructPlus(e.v(cols[1]), e.num(1)), x),
	}, opt.MakeColSet(cols[0]))
	e.expectNoMatch(rule, e.f.ConstructProject(computed, memo.ProjectionsExpr{}, opt.MakeColSet(x)))
}
