package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt"
	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
	"github.com/cockroachdb/cockroach/pkg/sql/types"
)

func TestJoinUnionTranspose(t *testing.T) {
	rule := loadRule(t, "JoinUnionTranspose")
	e := newEnv(t)
	x, xc := e.scan("t", 1)
	y, yc := e.scan("t", 1)
	o, oc := e.scan("u", 1)
	out := e.f.Metadata().AddColumn("union", types.Int)
	priv := &memo.SetPrivate{
		LeftCols:  opt.ColList{xc[0]},
		RightCols: opt.ColList{yc[0]},
		OutCols:   opt.ColList{out},
	}
	jp := &memo.JoinPrivate{}
	onU := e.filters(e.f.ConstructEq(e.v(out), e.v(oc[0])))
	onX := e.filters(e.f.ConstructEq(e.v(xc[0]), e.v(oc[0])))
	onY := e.filters(e.f.ConstructEq(e.v(yc[0]), e.v(oc[0])))

	before := e.f.ConstructInnerJoin(e.f.ConstructUnionAll(x, y, priv), o, onU, jp)
	want := e.f.ConstructUnionAll(
		e.f.ConstructInnerJoin(x, o, onX, jp),
		e.f.ConstructInnerJoin(y, o, onY, jp),
		priv,
	)
	e.expectRewrite(rule, before, want)

	e.expectNoMatch(rule, e.f.ConstructLeftJoin(e.f.ConstructUnionAll(x, y, priv), o, onU, jp))
	e.expectNoMatch(rule, e.f.ConstructInnerJoin(e.f.ConstructUnion(x, y, priv), o, onU, jp))
	e.expectNoMatch(rule, e.f.ConstructInnerJoin(x, o, onX, jp))
}
