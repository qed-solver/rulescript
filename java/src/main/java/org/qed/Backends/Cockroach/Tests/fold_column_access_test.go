package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt"
	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
	"github.com/cockroachdb/cockroach/pkg/sql/types"
)

func TestFoldColumnAccess(t *testing.T) {
	rule := loadRule(t, "FoldColumnAccess")
	e := newEnv(t)
	base, bc := e.scan("t", 3)
	x := e.f.Metadata().AddColumn("e0", types.Int)
	y := e.f.Metadata().AddColumn("e1", types.Int)
	tuple := e.f.ConstructProject(base, memo.ProjectionsExpr{
		e.f.ConstructProjectionsItem(e.v(bc[0]), x),
		e.f.ConstructProjectionsItem(e.f.ConstructPlus(e.v(bc[0]), e.num(1)), y),
	}, opt.ColSet{})
	before := e.f.ConstructProject(tuple, memo.ProjectionsExpr{}, opt.MakeColSet(y))
	want := e.f.ConstructProject(base, memo.ProjectionsExpr{
		e.f.ConstructProjectionsItem(e.f.ConstructPlus(e.v(bc[0]), e.num(1)), y),
	}, opt.ColSet{})
	e.expectRewrite(rule, before, want)

	z := e.f.Metadata().AddColumn("e2", types.Int)
	wide := e.f.ConstructProject(base, memo.ProjectionsExpr{
		e.f.ConstructProjectionsItem(e.v(bc[0]), x),
		e.f.ConstructProjectionsItem(e.f.ConstructPlus(e.v(bc[0]), e.num(1)), y),
		e.f.ConstructProjectionsItem(e.f.ConstructPlus(e.v(bc[0]), e.num(2)), z),
	}, opt.ColSet{})
	e.expectNoMatch(rule, e.f.ConstructProject(wide, memo.ProjectionsExpr{}, opt.MakeColSet(y)))
	e.expectNoMatch(rule, e.f.ConstructProject(tuple, memo.ProjectionsExpr{}, opt.MakeColSet(x)))
}
