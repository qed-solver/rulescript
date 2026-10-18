package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt"
	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
	"github.com/cockroachdb/cockroach/pkg/sql/types"
)

func TestPruneProjectSetCols(t *testing.T) {
	e := newEnv(t)
	rule := loadRule(t, "PruneProjectSetCols")

	left, leftCols := e.scan("t", 2)
	right, _ := e.scan("u", 1)
	predicate := e.f.ConstructEq(e.v(leftCols[0]), e.num(5))
	beforeJoin := e.f.ConstructInnerJoin(left, right, e.filters(predicate), &memo.JoinPrivate{})
	g := e.f.Metadata().AddColumn("g", types.Int)
	before := e.f.ConstructProject(beforeJoin, memo.ProjectionsExpr{e.f.ConstructProjectionsItem(e.v(leftCols[0]), g)}, opt.ColSet{})

	prunedLeft := e.f.ConstructProject(left, memo.EmptyProjectionsExpr, opt.MakeColSet(leftCols[0]))
	wantJoin := e.f.ConstructInnerJoin(prunedLeft, right, e.filters(e.f.ConstructEq(e.v(leftCols[0]), e.num(5))), &memo.JoinPrivate{})
	want := e.f.ConstructProject(wantJoin, memo.ProjectionsExpr{e.f.ConstructProjectionsItem(e.v(leftCols[0]), g)}, opt.ColSet{})
	e.expectRewrite(rule, before, want)

	badJoin := e.f.ConstructInnerJoin(left, right, e.filters(e.f.ConstructEq(e.v(leftCols[1]), e.num(5))), &memo.JoinPrivate{})
	bad := e.f.ConstructProject(badJoin, memo.ProjectionsExpr{e.f.ConstructProjectionsItem(e.v(leftCols[0]), g)}, opt.ColSet{})
	e.expectNoMatch(rule, bad)
}
