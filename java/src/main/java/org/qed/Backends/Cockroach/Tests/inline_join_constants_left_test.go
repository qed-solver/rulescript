package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt"
	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
	"github.com/cockroachdb/cockroach/pkg/sql/types"
)

func TestInlineJoinConstantsLeft(t *testing.T) {
	rule := loadRule(t, "InlineJoinConstantsLeft")
	e := newEnv(t)
	source, sc := e.scan("u", 1)
	other, oc := e.scan("t", 1)
	constantCol := e.f.Metadata().AddColumn("constant", types.Int)
	constant := e.intValues([]opt.ColumnID{constantCol}, []int{1})
	eq := e.filters(e.f.ConstructEq(e.v(sc[0]), e.v(constantCol)))
	inner := e.f.ConstructInnerJoin(source, constant, eq, &memo.JoinPrivate{})
	before := e.f.ConstructInnerJoin(inner, other,
		e.filters(e.f.ConstructEq(e.v(sc[0]), e.v(oc[0]))), &memo.JoinPrivate{})
	want := e.f.ConstructInnerJoin(inner, other,
		e.filters(e.f.ConstructEq(e.v(constantCol), e.v(oc[0]))), &memo.JoinPrivate{})
	e.expectRewrite(rule, before, want)
	e.expectNoMatch(rule, e.f.ConstructLeftJoin(inner, other,
		e.filters(e.f.ConstructEq(e.v(sc[0]), e.v(oc[0]))), &memo.JoinPrivate{}))
	wrongInner := e.f.ConstructInnerJoin(source, constant,
		e.filters(e.f.ConstructNe(e.v(sc[0]), e.v(constantCol))), &memo.JoinPrivate{})
	e.expectNoMatch(rule, e.f.ConstructInnerJoin(wrongInner, other,
		e.filters(e.f.ConstructEq(e.v(sc[0]), e.v(oc[0]))), &memo.JoinPrivate{}))
}

func TestSQLInlineJoinConstantsLeft(t *testing.T) {
	expectSQLFires(t, "InlineJoinConstantsLeft", "SELECT * FROM ((VALUES (1),(2)) AS s(x) JOIN (VALUES (1)) AS c(v) ON s.x=c.v) JOIN (VALUES (1),(3)) AS r(a) ON s.x=r.a")
}
