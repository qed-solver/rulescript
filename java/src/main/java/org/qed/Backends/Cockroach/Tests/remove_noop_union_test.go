package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt"
	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
	"github.com/cockroachdb/cockroach/pkg/sql/types"
)

func TestRemoveNoopUnion(t *testing.T) {
	rule := loadRule(t, "RemoveNoopUnion")
	e := newEnv(t)
	scan, cols := e.scan("t", 1)
	rightScan, rightCols := e.scan("t", 1)
	unionCol := e.f.Metadata().AddColumn("union", types.Int)
	union := e.f.ConstructUnionAll(scan, rightScan, &memo.SetPrivate{
		LeftCols: opt.ColList{cols[0]}, RightCols: opt.ColList{rightCols[0]}, OutCols: opt.ColList{unionCol},
	})
	before := e.f.ConstructGroupBy(union, nil, &memo.GroupingPrivate{GroupingCols: opt.MakeColSet(unionCol)})
	want := e.f.ConstructProject(
		e.f.ConstructGroupBy(scan, nil, &memo.GroupingPrivate{GroupingCols: opt.MakeColSet(cols[0])}),
		memo.ProjectionsExpr{e.f.ConstructProjectionsItem(e.v(cols[0]), unionCol)}, opt.ColSet{})
	e.expectRewrite(rule, before, want)

	left, leftCols := e.scan("t", 1)
	right, rightCols := e.scan("u", 1)
	nearUnionCol := e.f.Metadata().AddColumn("union", types.Int)
	nearUnion := e.f.ConstructUnionAll(left, right, &memo.SetPrivate{
		LeftCols: opt.ColList{leftCols[0]}, RightCols: opt.ColList{rightCols[0]}, OutCols: opt.ColList{nearUnionCol},
	})
	near := e.f.ConstructGroupBy(nearUnion, nil, &memo.GroupingPrivate{GroupingCols: opt.MakeColSet(nearUnionCol)})
	e.expectNoMatch(rule, near)
}
