package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt"
	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
	"github.com/cockroachdb/cockroach/pkg/sql/types"
)

func TestEliminateSetLeft(t *testing.T) {
	rule := loadRule(t, "EliminateSetLeft")
	e := newEnv(t)
	newCols := func(names ...string) []opt.ColumnID {
		out := make([]opt.ColumnID, len(names))
		for i, n := range names {
			out[i] = e.f.Metadata().AddColumn(n, types.Int)
		}
		return out
	}
	setPriv := func(l, r, out []opt.ColumnID) *memo.SetPrivate {
		return &memo.SetPrivate{
			LeftCols:  append(opt.ColList{}, l...),
			RightCols: append(opt.ColList{}, r...),
			OutCols:   append(opt.ColList{}, out...),
		}
	}

	scan, sc := e.scan("t", 3)
	rc := newCols("r0", "r1", "r2")
	o := newCols("o0", "o1", "o2")
	permuted := []opt.ColumnID{sc[2], sc[0], sc[1]}
	left := e.f.ConstructProject(scan, memo.ProjectionsExpr{}, opt.MakeColSet(sc...))
	empty := e.empty(rc...)

	before := e.f.ConstructUnionAll(left, empty, setPriv(permuted, rc, o))
	inner := e.f.ConstructProject(scan, memo.ProjectionsExpr{}, opt.MakeColSet(sc...))
	want := e.f.ConstructProject(inner, memo.ProjectionsExpr{
		e.f.ConstructProjectionsItem(e.v(sc[2]), o[0]),
		e.f.ConstructProjectionsItem(e.v(sc[0]), o[1]),
		e.f.ConstructProjectionsItem(e.v(sc[1]), o[2]),
	}, opt.ColSet{})
	e.expectRewrite(rule, before, want)

	nonEmpty := e.intValues(rc, []int{1, 2, 3})
	e.expectNoMatch(rule, e.f.ConstructUnionAll(left, nonEmpty, setPriv(permuted, rc, o)))
	e.expectNoMatch(rule, e.f.ConstructExceptAll(left, empty, setPriv(permuted, rc, o)))
	e.expectNoMatch(rule, e.f.ConstructUnionAll(scan, empty, setPriv(sc, rc, o)))
	e.expectNoMatch(rule, e.f.ConstructUnion(left, empty, setPriv(permuted, rc, o)))
}
