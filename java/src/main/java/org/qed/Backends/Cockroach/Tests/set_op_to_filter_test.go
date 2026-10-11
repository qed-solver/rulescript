package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt"
	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
	"github.com/cockroachdb/cockroach/pkg/sql/types"
)

func TestSetOpToFilter(t *testing.T) {
	rule := loadRule(t, "SetOpToFilter")
	e := newEnv(t)
	s, c := e.scan("t", 1)
	p1 := e.f.ConstructGt(e.v(c[0]), e.num(1))
	p2 := e.f.ConstructLt(e.v(c[0]), e.num(5))
	out := e.f.Metadata().AddColumn("out", types.Int)
	priv := &memo.SetPrivate{
		LeftCols:  opt.ColList{c[0]},
		RightCols: opt.ColList{c[0]},
		OutCols:   opt.ColList{out},
	}
	grouping := &memo.GroupingPrivate{GroupingCols: opt.MakeColSet(c[0])}

	before := e.f.ConstructUnion(
		e.f.ConstructSelect(s, e.filters(p1)),
		e.f.ConstructSelect(s, e.filters(p2)),
		priv,
	)
	want := e.f.ConstructGroupBy(
		e.f.ConstructSelect(s, e.filters(e.f.ConstructOr(p1, p2))),
		nil,
		grouping,
	)
	e.expectRewrite(rule, before, want)

	e.expectNoMatch(rule, e.f.ConstructUnionAll(
		e.f.ConstructSelect(s, e.filters(p1)),
		e.f.ConstructSelect(s, e.filters(p2)),
		priv,
	))
	e.expectNoMatch(rule, e.f.ConstructIntersect(
		e.f.ConstructSelect(s, e.filters(p1)),
		e.f.ConstructSelect(s, e.filters(p2)),
		priv,
	))

	other, oc := e.scan("u", 1)
	otherPriv := &memo.SetPrivate{
		LeftCols:  opt.ColList{c[0]},
		RightCols: opt.ColList{oc[0]},
		OutCols:   opt.ColList{out},
	}
	e.expectNoMatch(rule, e.f.ConstructUnion(
		e.f.ConstructSelect(s, e.filters(p1)),
		e.f.ConstructSelect(other, e.filters(p2)),
		otherPriv,
	))
	e.expectNoMatch(rule, e.f.ConstructUnion(
		e.f.ConstructSelect(s, e.filters(p1)),
		s,
		priv,
	))
}
