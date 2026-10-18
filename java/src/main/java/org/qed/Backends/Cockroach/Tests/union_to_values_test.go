package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt"
	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
	"github.com/cockroachdb/cockroach/pkg/sql/types"
)

func TestUnionToValues(t *testing.T) {
	rule := loadRule(t, "UnionToValues")
	e := newEnv(t)
	out := []opt.ColumnID{e.f.Metadata().AddColumn("out0", types.Int), e.f.Metadata().AddColumn("out1", types.Int)}
	lc := []opt.ColumnID{e.f.Metadata().AddColumn("l0", types.Int), e.f.Metadata().AddColumn("l1", types.Int)}
	mc := []opt.ColumnID{e.f.Metadata().AddColumn("m0", types.Int), e.f.Metadata().AddColumn("m1", types.Int)}
	rc := []opt.ColumnID{e.f.Metadata().AddColumn("r0", types.Int), e.f.Metadata().AddColumn("r1", types.Int)}
	root := []opt.ColumnID{e.f.Metadata().AddColumn("root0", types.Int), e.f.Metadata().AddColumn("root1", types.Int)}
	left := e.intValues(lc, []int{1, 2}, []int{3, 4})
	middle := e.intValues(mc, []int{3, 4}, []int{5, 6})
	right := e.intValues(rc, []int{7, 8})
	before := e.f.ConstructUnionAll(left, middle, &memo.SetPrivate{
		LeftCols: append(opt.ColList{}, lc...), RightCols: append(opt.ColList{}, mc...), OutCols: append(opt.ColList{}, out...),
	})
	before = e.f.ConstructUnionAll(before, right, &memo.SetPrivate{
		LeftCols: append(opt.ColList{}, out...), RightCols: append(opt.ColList{}, rc...), OutCols: append(opt.ColList{}, root...),
	})
	want := e.intValues(root, []int{1, 2}, []int{3, 4}, []int{3, 4}, []int{5, 6}, []int{7, 8})
	e.expectRewrite(rule, before, want)

	nearMiss := e.f.ConstructUnion(left, middle, &memo.SetPrivate{
		LeftCols: append(opt.ColList{}, lc...), RightCols: append(opt.ColList{}, mc...), OutCols: append(opt.ColList{}, out...),
	})
	nearMiss = e.f.ConstructUnionAll(nearMiss, right, &memo.SetPrivate{
		LeftCols: append(opt.ColList{}, out...), RightCols: append(opt.ColList{}, rc...), OutCols: append(opt.ColList{}, root...),
	})
	e.expectNoMatch(rule, nearMiss)
}

