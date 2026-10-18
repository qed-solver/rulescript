package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt"
	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
)

func TestTryDecorrelateProjectInnerJoin(t *testing.T) {
	e := newEnv(t)
	rule := loadRule(t, "TryDecorrelateProjectInnerJoin")

	l, lc := e.scan("nn", 1)
	il, ilc := e.scan("u", 1)
	ir, irc := e.scan("t", 1)
	innerOn := e.f.ConstructEq(e.v(ilc[0]), e.v(irc[0]))
	inner := e.f.ConstructInnerJoin(il, ir, e.filters(innerOn), &memo.JoinPrivate{})
	projected := e.f.Metadata().AddColumn("projected", e.v(ilc[0]).DataType())
	computed := e.f.ConstructPlus(e.v(ilc[0]), e.v(irc[0]))
	right := e.f.ConstructProject(inner,
		memo.ProjectionsExpr{e.f.ConstructProjectionsItem(computed, projected)},
		opt.MakeColSet(ilc[0]),
	)
	on := e.filters(
		e.f.ConstructEq(e.v(lc[0]), e.v(ilc[0])),
		e.f.ConstructEq(e.v(lc[0]), e.v(projected)),
	)
	before := e.f.ConstructLeftJoin(l, right, on, &memo.JoinPrivate{})

	expectedInner := e.f.ConstructInnerJoin(il, ir, nil, &memo.JoinPrivate{})
	rightAfter := e.f.ConstructProject(expectedInner,
		memo.ProjectionsExpr{e.f.ConstructProjectionsItem(computed, projected)},
		opt.MakeColSet(ilc[0], irc[0]),
	)
	expectedJoin := e.f.ConstructLeftJoin(l, rightAfter, e.filters(
		e.f.ConstructEq(e.v(lc[0]), e.v(ilc[0])),
		e.f.ConstructEq(e.v(lc[0]), e.v(projected)),
		innerOn,
	), &memo.JoinPrivate{})
	want := e.f.ConstructProject(expectedJoin, memo.EmptyProjectionsExpr, opt.MakeColSet(lc[0], ilc[0], projected))
	e.expectRewriteUpToFreshColumns(rule, before, want)

	nearMissInner := e.f.ConstructLeftJoin(il, ir, e.filters(innerOn), &memo.JoinPrivate{})
	nearMissRight := e.f.ConstructProject(nearMissInner,
		memo.ProjectionsExpr{e.f.ConstructProjectionsItem(computed, projected)},
		opt.MakeColSet(ilc[0]),
	)
	nearMiss := e.f.ConstructLeftJoin(l, nearMissRight, on, &memo.JoinPrivate{})
	e.expectNoMatch(rule, nearMiss)
}
