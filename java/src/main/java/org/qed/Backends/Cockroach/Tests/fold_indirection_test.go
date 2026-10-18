package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt"
	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
	"github.com/cockroachdb/cockroach/pkg/sql/types"
)

func TestFoldIndirection(t *testing.T) {
	rule := loadRule(t, "FoldIndirection")
	e := newEnv(t)
	s, sc := e.scan("t", 1)
	a := e.v(sc[0])
	c0 := e.f.Metadata().AddColumn("e0", types.Int)
	c1 := e.f.Metadata().AddColumn("e1", types.Int)
	c2 := e.f.Metadata().AddColumn("e2", types.Int)
	plusOne := e.f.ConstructPlus(a, e.num(1))

	arr := e.f.ConstructProject(s, memo.ProjectionsExpr{
		e.f.ConstructProjectionsItem(a, c0),
		e.f.ConstructProjectionsItem(plusOne, c1),
	}, opt.ColSet{})
	before := e.f.ConstructProject(arr, memo.ProjectionsExpr{}, opt.MakeColSet(c1))
	want := e.f.ConstructProject(s, memo.ProjectionsExpr{
		e.f.ConstructProjectionsItem(plusOne, c1),
	}, opt.ColSet{})
	e.expectRewriteUpToFreshColumns(rule, before, want)

	e.expectNoMatch(rule, e.f.ConstructProject(arr, memo.ProjectionsExpr{}, opt.MakeColSet(c0)))

	threeElems := e.f.ConstructProject(s, memo.ProjectionsExpr{
		e.f.ConstructProjectionsItem(a, c0),
		e.f.ConstructProjectionsItem(plusOne, c1),
		e.f.ConstructProjectionsItem(a, c2),
	}, opt.ColSet{})
	e.expectNoMatch(rule, e.f.ConstructProject(threeElems, memo.ProjectionsExpr{}, opt.MakeColSet(c1)))

	e.expectNoMatch(rule, e.f.ConstructProject(s, memo.ProjectionsExpr{
		e.f.ConstructProjectionsItem(plusOne, c1),
	}, opt.ColSet{}))
}
